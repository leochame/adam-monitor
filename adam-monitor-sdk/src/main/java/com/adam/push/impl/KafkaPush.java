package com.adam.push.impl;

import com.adam.utils.ProtostuffUtil;
import com.adam.entitys.LogMessage;
import com.adam.push.IPush;
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
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

// Kafka 推送实现
// 实现背压感知的推送策略
public class KafkaPush implements IPush {
    private KafkaProducer<String, byte[]> producer;
    private Semaphore semaphore;
    private MeterRegistry meterRegistry;

    private static final int MAX_IN_FLIGHT = 5000;
    private static final int BATCH_SIZE = 16384;
    private static final int LINGER_MS = 20;

    @Override
    public void open(String host, int port) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host + ":" + port);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, BATCH_SIZE);
        props.put(ProducerConfig.LINGER_MS_CONFIG, LINGER_MS);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        this.producer = new KafkaProducer<>(props);
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

        List<Future<RecordMetadata>> futures = new ArrayList<>();
        for (LogMessage log : batch) {
            ProducerRecord<String, byte[]> record = new ProducerRecord<>(
                    "logs-topic",
                    log.getSystemName(),
                    ProtostuffUtil.serialize(log)
            );

            futures.add(producer.send(record, (metadata, e) -> {
                semaphore.release();
                if (e != null) {
                    meterRegistry.counter("send.errors").increment();
                } else {
                    //todo 记录延迟
                    meterRegistry.timer("send.latency");
                }
            }));
        }

        meterRegistry.gauge("in.flight.requests", semaphore, Semaphore::availablePermits);
    }

    private boolean acquirePermits(int required) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 100) {
            if (semaphore.tryAcquire(required)) {
                return true;
            }
            Thread.yield();
        }
        return false;
    }

    @Override
    public void close() {
        producer.flush();
        producer.close(Duration.ofSeconds(30));
    }
}