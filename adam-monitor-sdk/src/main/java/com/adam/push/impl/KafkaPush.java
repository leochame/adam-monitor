package com.adam.push.impl;

import com.adam.entitys.LogMessage;
import com.adam.push.IPush;
import com.alibaba.fastjson.JSON;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// Kafka 推送实现
// 实现背压感知的推送策略，作为KFC架构中的数据收集层
public class KafkaPush implements IPush {
    private KafkaProducer<String, String> producer;
    private Semaphore semaphore;
    private MeterRegistry meterRegistry;

    private static int MAX_IN_FLIGHT = 5000;
    private static final int BATCH_SIZE = 32768;
    private static final int LINGER_MS = 50;

    // KFC架构中的Kafka主题名称，用于存储日志数据
    private static final String LOG_TOPIC = "adam-logs";
    
    @Override
    public void open(String host, int port) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host + ":" + port);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");  // 高效压缩算法
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, BATCH_SIZE);    // 批处理大小
        props.put(ProducerConfig.LINGER_MS_CONFIG, LINGER_MS);      // 等待时间以收集更多消息
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // 限制未确认请求数
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1048576); // 1MB 大包优化
        props.put(ProducerConfig.ACKS_CONFIG, "1");                // 平衡可靠性与吞吐量
        props.put(ProducerConfig.RETRIES_CONFIG, 3);                // 重试次数
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);     // 重试间隔
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);   // 32MB缓冲区
        this.producer = new KafkaProducer<String, String>(props);
        this.semaphore = new Semaphore(MAX_IN_FLIGHT);
        this.meterRegistry = Metrics.globalRegistry;
    }

    @Override
    public void send(LogMessage logMessage) {
        if (!acquirePermits(1)) {
            throw new RuntimeException("Too many pending requests");
        }
        
        // 确保时间戳存在，便于后续Flink处理时间窗口
        if (logMessage.getTimestamp() == null) {
            logMessage.setTimestamp(System.currentTimeMillis());
        }
        
        // 使用系统名称作为分区键，确保同一系统的日志进入同一分区，便于Flink处理
        ProducerRecord<String, String> record = new ProducerRecord<>(
                LOG_TOPIC,
                logMessage.getSystemName(),
                JSON.toJSONString(logMessage)
        );
        
        producer.send(record, (metadata, e) -> {
            semaphore.release(); // 释放信号量
            if (e != null) {
                meterRegistry.counter("send.errors").increment();
                // 记录错误详情，便于排查
                System.err.println("Failed to send log message: " + e.getMessage() + ", system: " + logMessage.getSystemName());
            } else {
                meterRegistry.timer("send.latency").record(Duration.ofMillis(
                        System.currentTimeMillis() - logMessage.getTimestamp()
                ));
            }
        });
    }

    @Override
    public void sendBatch(List<LogMessage> batch) {
        // 获取许可（背压控制），检查是否有足够的可用许可来发送批量的日志消息
        if (!acquirePermits(batch.size())) {
            throw new RuntimeException("Too many pending requests");
        }
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (LogMessage log : batch) {
            // 确保时间戳存在，便于后续Flink处理时间窗口
            if (log.getTimestamp() == null) {
                log.setTimestamp(System.currentTimeMillis());
            }
            
            // 使用系统名称作为分区键，确保同一系统的日志进入同一分区，便于Flink处理
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    LOG_TOPIC,
                    log.getSystemName(),
                    JSON.toJSONString(log)
            );

            CompletableFuture<Void> future = new CompletableFuture<>();
            producer.send(record, (metadata, e) -> {
                semaphore.release(); // 释放信号量
                if (e != null) {
                    meterRegistry.counter("send.errors").increment();
                    future.completeExceptionally(e); // 标记失败
                    // 记录错误详情，便于排查
                    System.err.println("Failed to send log message in batch: " + e.getMessage() + ", system: " + log.getSystemName());
                } else {
                    // 记录Kafka发送时间
                    meterRegistry.timer("send.latency").record(Duration.ofMillis(
                            System.currentTimeMillis() - log.getTimestamp()
                    ));
                    future.complete(null); // 标记成功
                }
            });
            futures.add(future);
        }
        
        // 监控当前正在处理的请求数量
        meterRegistry.gauge("in.flight.requests", semaphore, Semaphore::availablePermits);
        
        // 统一处理所有 Future，记录批量发送的成功率
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    // 批量发送成功完成的回调
                    meterRegistry.counter("send.batch.success").increment();
                })
                .exceptionally(ex -> {
                    // 批量发送出现异常的回调
                    meterRegistry.counter("send.batch.errors").increment();
                    return null;
                });
    }

    private boolean acquirePermits(int required) {
        try {
            return semaphore.tryAcquire(required, 100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    /**
     * 动态调整最大并发请求数，根据错误率和延迟情况
     * 在KFC架构中，这个方法可以帮助SDK层适应上游数据量的变化
     */
    private void adjustMaxInFlight() {
        double errorRate = meterRegistry.counter("send.errors").count();
        double avgLatency = meterRegistry.timer("send.latency").mean(TimeUnit.MILLISECONDS);
        
        // 根据错误率和延迟综合调整
        if (errorRate < 10 && avgLatency < 50) {
            // 错误率低且延迟小，可以增加并发
            MAX_IN_FLIGHT = (int) (MAX_IN_FLIGHT * 1.1);
        } else if (errorRate > 20 || avgLatency > 100) {
            // 错误率高或延迟大，需要减少并发
            MAX_IN_FLIGHT = (int) (MAX_IN_FLIGHT * 0.9);
        }
        
        // 设置上下限，避免极端情况
        MAX_IN_FLIGHT = Math.max(1000, Math.min(MAX_IN_FLIGHT, 10000));
        
        // 记录调整后的值，便于监控
        meterRegistry.gauge("max.in.flight", MAX_IN_FLIGHT);
    }

    @Override
    public void close() {
        try {
            // 先刷新所有缓冲区中的数据
            producer.flush();
            // 优雅关闭生产者，等待所有未完成的请求完成
            producer.close(Duration.ofSeconds(30));
            System.out.println("Kafka producer closed successfully");
        } catch (Exception e) {
            // 记录关闭过程中的异常
            System.err.println("Error closing Kafka producer: " + e.getMessage());
            meterRegistry.counter("close.errors").increment();
        } finally {
            // 记录关闭事件
            meterRegistry.counter("producer.closed").increment();
        }
    }
}