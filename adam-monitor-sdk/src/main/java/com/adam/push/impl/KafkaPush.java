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
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// Kafka 推送实现
// 实现背压感知的推送策略
public class KafkaPush implements IPush {
    private KafkaProducer<String, String> producer;
    private Semaphore semaphore;
    private MeterRegistry meterRegistry;

    private static int MAX_IN_FLIGHT = 5000;
    private static final int BATCH_SIZE = 32768;
    private static final int LINGER_MS = 50;

    @Override
    public void open(String host, int port) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host + ":" + port);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, BATCH_SIZE);
        props.put(ProducerConfig.LINGER_MS_CONFIG, LINGER_MS);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);// 默认 20ms -> 50ms
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1048576); // 1MB 大包优化
        props.put(ProducerConfig.ACKS_CONFIG, "1");         // 平衡可靠性与吞吐量
        this.producer = new KafkaProducer<String, String>(props);
        this.semaphore = new Semaphore(MAX_IN_FLIGHT);
        this.meterRegistry = Metrics.globalRegistry;
    }

    @Override
    public void send(LogMessage logMessage) {
    }

    @Override
    public void sendBatch(List<LogMessage> batch) {
        //获取许可（背压控制），这段代码检查是否有足够的可用许可来发送批量的日志消息。
        if (!acquirePermits(batch.size())) {
            throw new RuntimeException("Too many pending requests");
        }
        semaphore.release();
//        List<CompletableFuture<Void>> futures = new ArrayList<>();
//        for (LogMessage log : batch) {
//            ProducerRecord<String, String> record = new ProducerRecord<>(
//                    "logs-topic4",
//                    log.getSystemName(),
//                    JSON.toJSONString(log)
//            );
//
//            CompletableFuture<Void> future = new CompletableFuture<>();
//            producer.send(record, (metadata, e) -> {
//                semaphore.release(); // 释放信号量
//                if (e != null) {
//                    meterRegistry.counter("send.errors").increment();
//                    future.completeExceptionally(e); // 标记失败
//                } else {
//                    //TODO 记录Kafka发送时间
////                    meterRegistry.timer("send.latency").record(System.currentTimeMillis() - log.getTimestamp());
//                    meterRegistry.timer("send.latency");
//                    future.complete(null); // 标记成功
//                }
//            });
//            futures.add(future);
//        }
////        meterRegistry.gauge("in.flight.requests", semaphore, Semaphore::availablePermits);
//        // 统一处理所有 Future
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
//                .exceptionally(ex -> {
//                    meterRegistry.counter("send.batch.errors").increment();
//                    return null;
//                });
    }

    private boolean acquirePermits(int required) {
        try {
            return semaphore.tryAcquire(required, 100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    private void adjustMaxInFlight() {
        double errorRate = meterRegistry.counter("send.errors").count();
        if (errorRate < 10) {
            MAX_IN_FLIGHT = (int) (MAX_IN_FLIGHT * 1.1);
        } else {
            MAX_IN_FLIGHT = (int) (MAX_IN_FLIGHT * 0.9);
        }
        MAX_IN_FLIGHT = Math.min(MAX_IN_FLIGHT, 10000); // 设置上限
    }

    @Override
    public void close() {
        producer.flush();
        producer.close(Duration.ofSeconds(30));
    }
}