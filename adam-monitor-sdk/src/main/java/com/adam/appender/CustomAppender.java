package com.adam.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.adam.entitys.LogMessage;
import com.adam.time.EnhancedNTPClient;
import com.adam.trace.AdamTraceContext;
import com.adam.utils.LocalFileBuffer;
import com.alibaba.fastjson.JSON;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;

/**
 * 高性能日志采集器
 * 特性：
 * 1. 智能分片负载均衡
 * 2. 动态批量大小调整
 * 3. 多级背压处理
 * 4. NTP时间同步
 * 5. 性能监控与自适应优化
 */
public class CustomAppender<E> extends UnsynchronizedAppenderBase<E> {

    // 配置参数
    private String systemName;
    private String groupId;
    private String host;
    private int port;
    private String topic = "adam-monitor-logs";

    // Kafka Producer配置
    private volatile int batchSize = 16384;      // 16KB，利用Kafka原生批量
    private volatile int lingerMs = 100;         // 100ms延迟，允许更多消息聚合
    private volatile int maxInFlight = 5;        // 并发请求数
    private volatile int bufferMemory = 33554432; // 32MB内存缓冲

    // 核心组件
    private KafkaProducer<String, String> producer;
    private LocalFileBuffer localFileBuffer;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 简化的监控指标
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong successMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    private final AtomicLong droppedMessages = new AtomicLong(0);

    // 背压控制
    private volatile long maxPendingMessages = 10000;
    private final Semaphore backpressureControl = new Semaphore((int) maxPendingMessages);

    @Override
    public void start() {
        if (running.get()) {
            return;
        }

        try {
            // 初始化Kafka Producer
            initializeKafkaProducer();

            // 初始化本地文件缓冲区
            this.localFileBuffer = new LocalFileBuffer(systemName);

            running.set(true);
            super.start();

            addInfo("SimplifiedKafkaAppender started successfully");

        } catch (Exception e) {
            addError("Failed to start SimplifiedKafkaAppender", e);
            throw new RuntimeException("Appender startup failed", e);
        }
    }

    private void initializeKafkaProducer() {
        Properties props = new Properties();

        // 基础配置
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, host + ":" + port);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // 批量优化配置 - 直接利用Kafka原生机制
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);

        // 可靠性配置
        props.put(ProducerConfig.ACKS_CONFIG, "1");                    // 平衡性能与可靠性
        props.put(ProducerConfig.RETRIES_CONFIG, 3);                   // 重试3次
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);       // 重试间隔1秒
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);    // 请求超时30秒

        // 性能配置
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, maxInFlight);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");   // 压缩减少网络开销

        // 分区策略 - 利用Kafka原生负载均衡
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,
                "org.apache.kafka.clients.producer.RoundRobinPartitioner");

        this.producer = new KafkaProducer<>(props);

        addInfo("Kafka Producer initialized with batch.size=" + batchSize +
                ", linger.ms=" + lingerMs + ", buffer.memory=" + bufferMemory);
    }
    @Override
    public void append(E eventObject) {
        if (!running.get() || !(eventObject instanceof ILoggingEvent)) {
            return;
        }

        ILoggingEvent event = (ILoggingEvent) eventObject;

        // 过滤非目标包的日志
        StackTraceElement[] callerData = event.getCallerData();
        if (callerData == null || callerData.length == 0) return;

        String className = callerData[0].getClassName();
        if (groupId != null && !className.startsWith(groupId)) return;

        // 创建日志消息
        LogMessage logMessage = createLogMessage(event, callerData[0]);

        // 背压控制：如果待处理消息过多，尝试获取许可
        if (!backpressureControl.tryAcquire()) {
            handleBackpressure(logMessage);
            return;
        }

        totalMessages.incrementAndGet();

        // 异步发送到Kafka - 利用Kafka原生批量机制
        sendAsync(logMessage);
    }

    private LogMessage createLogMessage(ILoggingEvent event, StackTraceElement caller) {
        LogMessage logMessage = new LogMessage(
                systemName,
                caller.getClassName(),
                caller.getMethodName(),
                event.getFormattedMessage(),
                event.getTimeStamp()
        );

        // 设置当前的AID和TraceId
        logMessage.setAid(AdamTraceContext.getAid());
        logMessage.setTraceId(AdamTraceContext.getTraceId());

        return logMessage;
    }

    private void sendAsync(LogMessage logMessage) {
        try {
            // 构建Kafka消息记录
            String key = generateMessageKey(logMessage);
            String value = JSON.toJSONString(logMessage);

            ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic, key, value);

            // 添加AID到消息头
            String aid = logMessage.getAid();
            if (aid != null) {
                record.headers().add(AdamTraceContext.getAidHeaderKey(), aid.getBytes());
            }

            // 添加TraceId到消息头
            String traceId = logMessage.getTraceId();
            if (traceId != null) {
                record.headers().add("X-Trace-ID", traceId.getBytes());
            }

            // 异步发送 - Kafka会自动处理批量聚合
            producer.send(record, new CustomAppender.SendCallback());

        } catch (Exception e) {
            // 释放背压控制许可
            backpressureControl.release();
            failedMessages.incrementAndGet();
            addError("Failed to send log message", e);

            // 降级到本地文件
            localFileBuffer.offer(logMessage);
        }
    }

    /**
     * 生成消息键，用于Kafka分区
     * 基于系统名称和类名的哈希，实现负载均衡
     */
    private String generateMessageKey(LogMessage logMessage) {
        return systemName + ":" + logMessage.getClassName();
    }

    /**
     * 简化的背压处理
     */
    private void handleBackpressure(LogMessage logMessage) {
        droppedMessages.incrementAndGet();

        // 策略1: 重要日志写入本地文件
        if (isImportantLog(logMessage)) {
            localFileBuffer.offer(logMessage);
            addWarn("Important log message saved to local buffer due to backpressure");
        } else {
            // 策略2: 直接丢弃普通日志
            addWarn("Log message dropped due to backpressure: " +
                    logMessage.getClassName() + "." + logMessage.getMethodName());
        }
    }
    /**
     * 判断是否为重要日志
     */
    private boolean isImportantLog(LogMessage logMessage) {
        String content = logMessage.getContent().toLowerCase();
        return content.contains("error") ||
                content.contains("exception") ||
                content.contains("failed") ||
                content.contains("timeout");
    }

    /**
     * 发送回调 - 处理发送结果
     */
    private class SendCallback implements Callback {
        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            // 释放背压控制许可
            backpressureControl.release();

            if (exception == null) {
                successMessages.incrementAndGet();
            } else {
                failedMessages.incrementAndGet();
                addError("Message send failed", exception);
            }
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            addInfo("Stopping SimplifiedKafkaAppender...");

            // 等待所有消息发送完成
            if (producer != null) {
                producer.flush();  // 强制发送所有批量消息
                producer.close(Duration.ofSeconds(30));
            }

            // 关闭本地文件缓冲区
            if (localFileBuffer != null) {
                localFileBuffer.close();
            }

            printStatistics();
            super.stop();

            addInfo("SimplifiedKafkaAppender stopped successfully");

        } catch (Exception e) {
            addError("Error during appender shutdown", e);
        }
    }

    /**
     * 打印统计信息
     */
    private void printStatistics() {
        long total = totalMessages.get();
        long success = successMessages.get();
        long failed = failedMessages.get();
        long dropped = droppedMessages.get();

        double successRate = total > 0 ? (double) success / total * 100 : 0;
        double failureRate = total > 0 ? (double) failed / total * 100 : 0;
        double dropRate = total > 0 ? (double) dropped / total * 100 : 0;

        System.out.println("=== SimplifiedKafkaAppender Statistics ===");
        System.out.println("Total Messages: " + total);
        System.out.println("Success Messages: " + success + " (" + String.format("%.2f%%", successRate) + ")");
        System.out.println("Failed Messages: " + failed + " (" + String.format("%.2f%%", failureRate) + ")");
        System.out.println("Dropped Messages: " + dropped + " (" + String.format("%.2f%%", dropRate) + ")");
        System.out.println("Configuration: batch.size=" + batchSize + ", linger.ms=" + lingerMs);
        System.out.println("=====================================");
    }

    // Configuration getters and setters
    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

    public int getLingerMs() { return lingerMs; }
    public void setLingerMs(int lingerMs) { this.lingerMs = lingerMs; }

    public int getMaxInFlight() { return maxInFlight; }
    public void setMaxInFlight(int maxInFlight) { this.maxInFlight = maxInFlight; }

    public long getMaxPendingMessages() { return maxPendingMessages; }
    public void setMaxPendingMessages(long maxPendingMessages) {
        this.maxPendingMessages = maxPendingMessages;
    }


}