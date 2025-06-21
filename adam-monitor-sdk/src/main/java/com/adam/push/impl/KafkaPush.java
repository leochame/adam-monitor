package com.adam.push.impl;

import com.adam.entitys.LogMessage;
import com.adam.push.IPush;
import com.alibaba.fastjson.JSON;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.errors.RetriableException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 优化的Kafka推送实现
 * 特性：
 * 1. 智能背压控制
 * 2. 动态参数调整
 * 3. 批量发送优化
 * 4. 错误恢复机制
 * 5. 性能监控
 */
public class KafkaPush implements IPush {
    private KafkaProducer<String, String> producer;
    private Semaphore semaphore;
    private MeterRegistry meterRegistry;
    
    // 动态配置参数
    private volatile int MAX_IN_FLIGHT = 1000;
    private volatile int MIN_IN_FLIGHT = 100;
    private volatile int MAX_IN_FLIGHT_LIMIT = 5000;
    private static final int BATCH_SIZE = 32768;
    private static final int LINGER_MS = 50;
    private static final String TOPIC = "adam-monitor-logs";
    
    // 性能监控
    private final AtomicLong totalSendCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong avgLatency = new AtomicLong(0);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    // 动态调整
    private volatile long lastAdjustTime = System.currentTimeMillis();
    private static final long ADJUST_INTERVAL = 30_000; // 30秒调整一次
    
    // 重试机制
    private ScheduledExecutorService retryExecutor;
    private final int maxRetries = 3;
    private final long baseRetryDelayMs = 1000;

    @Override
    public void open(String host, int port) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host + ":" + port);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // 优化的配置
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // 更好的压缩性能
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, BATCH_SIZE);
        props.put(ProducerConfig.LINGER_MS_CONFIG, LINGER_MS);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 3); // 减少以提高顺序性
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 2097152); // 2MB 大包优化
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // 平衡可靠性与吞吐量
        props.put(ProducerConfig.RETRIES_CONFIG, maxRetries);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 60000);
        
        // 幂等性配置
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // 分区策略
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, 
                 "org.apache.kafka.clients.producer.RoundRobinPartitioner");
        
        this.producer = new KafkaProducer<>(props);
        this.semaphore = new Semaphore(MAX_IN_FLIGHT);
        this.meterRegistry = Metrics.globalRegistry;
        this.retryExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "KafkaPush-Retry"));
    }

    @Override
    public void send(LogMessage logMessage) {
        if (closed.get()) {
            throw new IllegalStateException("KafkaPush is closed");
        }
        
        if (!acquirePermits(1)) {
            throw new RuntimeException("Failed to acquire permit for sending");
        }
        
        long startTime = System.nanoTime();
        totalSendCount.incrementAndGet();
        
        // 使用时间戳作为分区键，提高分区均匀性
        String partitionKey = String.valueOf(logMessage.getTimestamp() % 100);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                TOPIC,
                partitionKey,
                JSON.toJSONString(logMessage)
        );
        
        producer.send(record, (metadata, e) -> {
            semaphore.release(); // 释放信号量
            long endTime = System.nanoTime();
            long latency = Duration.ofNanos(endTime - startTime).toMillis();
            
            if (e != null) {
                errorCount.incrementAndGet();
                meterRegistry.counter("send.errors").increment();
            } else {
                successCount.incrementAndGet();
                updateLatencyStats(latency);
                meterRegistry.timer("send.latency").record(Duration.ofMillis(latency));
            }
        });
    }

    @Override
    public void sendBatch(List<LogMessage> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        
        if (closed.get()) {
            throw new IllegalStateException("KafkaPush is closed");
        }
        
        // 获取许可（背压控制）
        if (!acquirePermits(batch.size())) {
            throw new RuntimeException("Failed to acquire permits for batch sending");
        }
        
        long startTime = System.nanoTime();
        totalSendCount.addAndGet(batch.size());
        
        try {
            List<CompletableFuture<RecordMetadata>> futures = new ArrayList<>();
            
            for (LogMessage log : batch) {
                CompletableFuture<RecordMetadata> future = sendAsyncWithRetry(log, 0);
                futures.add(future);
            }
            
            // 等待所有发送完成
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
            
            allFutures.get(30, TimeUnit.SECONDS);
            
            // 统计成功数量
            long successfulSends = futures.stream()
                .mapToLong(f -> {
                    try {
                        f.get();
                        return 1;
                    } catch (Exception e) {
                        return 0;
                    }
                })
                .sum();
            
            successCount.addAndGet(successfulSends);
            errorCount.addAndGet(batch.size() - successfulSends);
            
            // 更新延迟统计
            long endTime = System.nanoTime();
            updateLatencyStats(Duration.ofNanos(endTime - startTime).toMillis());
            
            // 更新监控指标
            meterRegistry.counter("send.batch.total").increment();
            if (successfulSends == batch.size()) {
                meterRegistry.counter("send.batch.success").increment();
            } else {
                meterRegistry.counter("send.batch.errors").increment();
            }
            
        } catch (Exception e) {
            errorCount.addAndGet(batch.size());
            meterRegistry.counter("send.batch.errors").increment();
            throw new RuntimeException("Batch send failed", e);
        } finally {
            // 动态调整MAX_IN_FLIGHT
            adjustMaxInFlight();
        }
    }

    /**
     * 带重试的异步发送
     */
    private CompletableFuture<RecordMetadata> sendAsyncWithRetry(LogMessage message, int retryCount) {
        CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
        
        // 使用时间戳作为分区键，提高分区均匀性
        String partitionKey = String.valueOf(message.getTimestamp() % 100);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                TOPIC, partitionKey, JSON.toJSONString(message));
        
        producer.send(record, (metadata, exception) -> {
            semaphore.release();
            if (exception != null) {
                // 判断是否需要重试
                if (retryCount < maxRetries && isRetriableException(exception)) {
                    // 延迟重试
                    long delay = calculateRetryDelay(retryCount);
                    retryExecutor.schedule(() -> {
                        sendAsyncWithRetry(message, retryCount + 1)
                            .whenComplete((result, error) -> {
                                if (error != null) {
                                    future.completeExceptionally(error);
                                } else {
                                    future.complete(result);
                                }
                            });
                    }, delay, TimeUnit.MILLISECONDS);
                } else {
                    future.completeExceptionally(exception);
                }
            } else {
                future.complete(metadata);
            }
        });
        
        return future;
    }
    
    private boolean acquirePermits(int required) {
        try {
            // 动态调整超时时间
            long timeout = Math.max(1000, avgLatency.get() * 2);
            return semaphore.tryAcquire(required, timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * 智能调整最大并发数
     */
    private void adjustMaxInFlight() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAdjustTime < ADJUST_INTERVAL) {
            return;
        }
        
        lastAdjustTime = currentTime;
        long totalSent = totalSendCount.get();
        long errors = errorCount.get();
        long successful = successCount.get();
        
        if (totalSent > 100) { // 有足够的样本数据
            double errorRate = (double) errors / totalSent;
            long currentLatency = avgLatency.get();
            
            int oldMaxInFlight = MAX_IN_FLIGHT;

            if (errorRate > 0.1) {
                // 错误率过高，大幅减少并发
                MAX_IN_FLIGHT = Math.max(MIN_IN_FLIGHT, MAX_IN_FLIGHT - 200);
            } else if (errorRate > 0.05) {
                // 错误率较高，适度减少并发
                MAX_IN_FLIGHT = Math.max(MIN_IN_FLIGHT, MAX_IN_FLIGHT - 100);
            } else if (errorRate < 0.01 && currentLatency < 100) {
                // 错误率很低且延迟较小，可以增加并发
                MAX_IN_FLIGHT = Math.min(MAX_IN_FLIGHT_LIMIT, MAX_IN_FLIGHT + 100);
            } else if (errorRate < 0.02 && currentLatency < 200) {
                // 错误率较低，适度增加并发
                MAX_IN_FLIGHT = Math.min(MAX_IN_FLIGHT_LIMIT, MAX_IN_FLIGHT + 50);
            }
            
            // 记录调整信息
            if (MAX_IN_FLIGHT != oldMaxInFlight) {
                System.out.printf("Adjusted MAX_IN_FLIGHT: %d -> %d (ErrorRate: %.2f%%, Latency: %dms)%n",
                    oldMaxInFlight, MAX_IN_FLIGHT, errorRate * 100, currentLatency);
            }
        }
    }

    /**
     * 判断是否为可重试异常
     */
    private boolean isRetriableException(Exception exception) {
        return exception instanceof RetriableException ||
               exception instanceof TimeoutException ||
               (exception.getMessage() != null && 
                (exception.getMessage().contains("timeout") ||
                 exception.getMessage().contains("connection")));
    }
    
    /**
     * 计算重试延迟（指数退避）
     */
    private long calculateRetryDelay(int retryCount) {
        return baseRetryDelayMs * (1L << retryCount); // 指数退避
    }
    
    /**
     * 更新延迟统计
     */
    private void updateLatencyStats(long latencyMs) {
        long currentAvg = avgLatency.get();
        long newAvg = (currentAvg + latencyMs) / 2;
        avgLatency.set(newAvg);
    }
    
    /**
     * 获取性能统计
     */
    public KafkaStats getStats() {
        return new KafkaStats(
            totalSendCount.get(),
            successCount.get(),
            errorCount.get(),
            avgLatency.get(),
            MAX_IN_FLIGHT,
            semaphore.availablePermits()
        );
    }
    
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                // 关闭重试执行器
                if (retryExecutor != null) {
                    retryExecutor.shutdown();
                    if (!retryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        retryExecutor.shutdownNow();
                    }
                }
                
                // 刷新并关闭生产者
                if (producer != null) {
                    producer.flush();
                    producer.close(Duration.ofSeconds(10));
                }
                
                // 输出最终统计
                KafkaStats stats = getStats();
                System.out.println("=== KafkaPush Final Statistics ===");
                System.out.println("Total Sent: " + stats.totalSent);
                System.out.println("Successful: " + stats.successful);
                System.out.println("Errors: " + stats.errors);
                System.out.println("Success Rate: " + String.format("%.2f%%", stats.getSuccessRate() * 100));
                System.out.println("Average Latency: " + stats.avgLatency + "ms");
                System.out.println("Final MAX_IN_FLIGHT: " + stats.maxInFlight);
                System.out.println("=================================");
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Kafka统计信息
     */
    public static class KafkaStats {
        public final long totalSent;
        public final long successful;
        public final long errors;
        public final long avgLatency;
        public final int maxInFlight;
        public final int availablePermits;
        
        public KafkaStats(long totalSent, long successful, long errors, 
                         long avgLatency, int maxInFlight, int availablePermits) {
            this.totalSent = totalSent;
            this.successful = successful;
            this.errors = errors;
            this.avgLatency = avgLatency;
            this.maxInFlight = maxInFlight;
            this.availablePermits = availablePermits;
        }
        
        public double getSuccessRate() {
            return totalSent > 0 ? (double) successful / totalSent : 0.0;
        }
        
        public double getErrorRate() {
            return totalSent > 0 ? (double) errors / totalSent : 0.0;
        }
    }
}