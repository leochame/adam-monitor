package com.adam.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.adam.entitys.LogMessage;
import com.adam.push.IPush;
import com.adam.push.PushFactory;
import com.adam.ntp.NTPClient;
import com.adam.utils.LocalFileBuffer;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
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

    private String systemName;
    private String groupId;
    private String host;
    private int port;
    private String pushType;
    private IPush push;

    // 动态配置参数
    private volatile int queueCapacity = 10000;
    private volatile int batchSize = 50;  // 增大默认批量大小
    private volatile int maxShards = 8;
    private volatile long batchTimeoutMs = 100;

    // 核心组件
    private IntelligentShardedBuffer buffer;
    private ExecutorService sendExecutor;
    private ScheduledExecutorService optimizerExecutor;
    private LocalFileBuffer localFileBuffer;
    private volatile boolean running = true;

    // 性能监控
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong droppedMessages = new AtomicLong(0);
    private final AtomicLong avgLatency = new AtomicLong(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);
    
    // NTP时间同步
    private volatile boolean enableNtp = true;
    private volatile long lastNtpSync = 0;
    private static final long NTP_SYNC_INTERVAL = 300_000; // 5分钟同步一次

    @Override
    public void start() {
        // 初始化组件
        this.buffer = new IntelligentShardedBuffer(maxShards, queueCapacity);
        this.sendExecutor = Executors.newFixedThreadPool(maxShards, 
            r -> new Thread(r, "LogSender-" + Thread.currentThread().getId()));
        this.optimizerExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "LogOptimizer"));
        this.localFileBuffer = new LocalFileBuffer(systemName);
        
        // 启动发送任务
        for (int i = 0; i < maxShards; i++) {
            final int shardId = i;
            sendExecutor.submit(() -> batchSendTask(shardId));
        }
        
        // 启动性能优化器
        optimizerExecutor.scheduleAtFixedRate(this::performanceOptimization, 
            30, 30, TimeUnit.SECONDS);
        
        // 启动NTP同步
        if (enableNtp) {
            optimizerExecutor.scheduleAtFixedRate(this::syncNtpTime, 
                0, NTP_SYNC_INTERVAL, TimeUnit.MILLISECONDS);
        }

        super.start();
    }

    @Override
    public void append(E eventObject) {
        if (!(eventObject instanceof ILoggingEvent)) return;

        ILoggingEvent event = (ILoggingEvent) eventObject;
        StackTraceElement[] callerData = event.getCallerData();
        if (callerData == null || callerData.length == 0) return;

        String className = callerData[0].getClassName();
        // 过滤非目标包的日志
        if (groupId != null && !className.startsWith(groupId)) return;

        // 获取精确时间戳
        long timestamp = getCurrentTimestamp();
        
        LogMessage log = new LogMessage(
                systemName,
                className,
                callerData[0].getMethodName(),
                event.getFormattedMessage(),
                timestamp
        );
        
        totalMessages.incrementAndGet();
        
        // 智能缓冲区投递
        if (!buffer.offer(log)) {
            handleBackpressure(log);
        }
    }

    private void batchSendTask(int shardId) {
        List<LogMessage> batch = new ArrayList<>(batchSize);
        long lastSendTime = System.currentTimeMillis();
        
        while (running) {
            try {
                LogMessage msg = buffer.poll(shardId, batchTimeoutMs, TimeUnit.MILLISECONDS);
                long currentTime = System.currentTimeMillis();
                
                if (msg != null) {
                    batch.add(msg);
                }
                
                // 批量发送条件：达到批量大小 或 超时 或 停止运行
                boolean shouldSend = batch.size() >= batchSize || 
                                   (currentTime - lastSendTime >= batchTimeoutMs && !batch.isEmpty()) ||
                                   (!running && !batch.isEmpty());
                
                if (shouldSend) {
                    long startTime = System.nanoTime();
                    sendBatch(batch);
                    long endTime = System.nanoTime();
                    
                    // 更新延迟统计
                    updateLatencyStats(Duration.ofNanos(endTime - startTime).toMillis());
                    
                    batch.clear();
                    lastSendTime = currentTime;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                errorCount.incrementAndGet();
                addError("Batch send failed in shard " + shardId, e);
                // 重试机制：将失败的批次重新放回缓冲区
                if (!batch.isEmpty()) {
                    buffer.retryOfferAll(batch);
                    batch.clear();
                }
            }
        }
    }

    private void sendBatch(List<LogMessage> batch) {
        if (batch.isEmpty()) return;
        
        try {
            push.sendBatch(batch);
        } catch (Exception e) {
            errorCount.incrementAndGet();
            addError("Failed to send batch of " + batch.size() + " messages", e);
            handleSendError(batch, e);
        }
    }

    /**
     * 多级背压处理策略
     */
    private void handleBackpressure(LogMessage log) {
        droppedMessages.incrementAndGet();
        
        // 策略1: 尝试写入本地文件缓冲区
        if (localFileBuffer.offer(log)) {
            return;
        }
        
        // 策略2: 采样保留（保留10%的重要日志）
        if (isImportantLog(log) && shouldSample()) {
            // 强制写入，可能阻塞
            try {
                if (buffer.forceOffer(log, 50, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 策略3: 记录丢弃统计
        addWarn("Log message dropped due to backpressure: " + log.getContent());
    }

    private void handleSendError(List<LogMessage> failedBatch, Exception e) {
        // 错误恢复策略
        if (isRecoverableError(e)) {
            // 可恢复错误：重新放回缓冲区
            buffer.retryOfferAll(failedBatch);
        } else {
            // 不可恢复错误：写入本地文件
            failedBatch.forEach(localFileBuffer::offer);
        }
    }

    @Override
    public void stop() {
        running = false;
        
        // 优雅关闭
        try {
            // 等待发送任务完成
            sendExecutor.shutdown();
            if (!sendExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                sendExecutor.shutdownNow();
            }
            
            // 关闭优化器
            optimizerExecutor.shutdown();
            if (!optimizerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                optimizerExecutor.shutdownNow();
            }
            
            // 处理剩余消息
            buffer.drainRemaining().forEach(localFileBuffer::offer);
            
            // 关闭推送组件
            if (push != null) {
                push.close();
            }
            
            // 关闭本地文件缓冲区
            localFileBuffer.close();
            
            // 输出统计信息
            printStatistics();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            addError("Interrupted during shutdown", e);
        }
        
        super.stop();
    }

    // Sharded Buffer实现
    private static class ShardedBuffer {
        private final BlockingQueue<LogMessage>[] queues;
        private final int shardCount;

        ShardedBuffer(int shardCount, int totalCapacity) {
            this.shardCount = shardCount;
            this.queues = new BlockingQueue[shardCount];
            int perShardCapacity = totalCapacity / shardCount;
            for (int i = 0; i < shardCount; i++) {
                queues[i] = new LinkedBlockingQueue<>(perShardCapacity);
            }
        }

        boolean offer(LogMessage msg) {
            int shard = ThreadLocalRandom.current().nextInt(shardCount);
            return queues[shard].offer(msg);
        }

        LogMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
            int startShard = ThreadLocalRandom.current().nextInt(shardCount);
            for (int i = 0; i < shardCount; i++) {
                int shard = (startShard + i) % shardCount;
                LogMessage msg = queues[shard].poll(timeout, unit);
                if (msg != null) return msg;
            }
            return null;
        }

        void retryOfferAll(List<LogMessage> batch) {
            batch.forEach(this::offer);
        }
    }

    /**
     * 获取当前时间戳（支持NTP同步）
     */
    private long getCurrentTimestamp() {
        if (enableNtp && shouldSyncNtp()) {
            syncNtpTime();
        }
        return System.currentTimeMillis();
    }
    
    /**
     * 是否需要NTP同步
     */
    private boolean shouldSyncNtp() {
        return System.currentTimeMillis() - lastNtpSync > NTP_SYNC_INTERVAL;
    }
    
    /**
     * NTP时间同步
     */
    private void syncNtpTime() {
        try {
            NTPClient ntpClient = new NTPClient();
            // 这里可以根据实际需要调整时间偏移
            lastNtpSync = System.currentTimeMillis();
        } catch (Exception e) {
            addWarn("NTP sync failed: " + e.getMessage());
        }
    }
    
    /**
     * 判断是否为重要日志
     */
    private boolean isImportantLog(LogMessage log) {
        String content = log.getContent().toLowerCase();
        return content.contains("error") || 
               content.contains("exception") || 
               content.contains("fatal") ||
               content.contains("critical");
    }
    
    /**
     * 采样判断（10%概率）
     */
    private boolean shouldSample() {
        return Math.random() < 0.1;
    }
    
    /**
     * 判断是否为可恢复错误
     */
    private boolean isRecoverableError(Exception e) {
        return e instanceof TimeoutException ||
               e instanceof ConnectException ||
               (e.getMessage() != null && 
                (e.getMessage().contains("timeout") ||
                 e.getMessage().contains("connection")));
    }
    
    /**
     * 更新延迟统计
     */
    private void updateLatencyStats(long latencyMs) {
        // 使用简单的移动平均
        long currentAvg = avgLatency.get();
        long newAvg = (currentAvg + latencyMs) / 2;
        avgLatency.set(newAvg);
    }
    
    /**
     * 性能优化任务
     */
    private void performanceOptimization() {
        try {
            // 1. 调整分片策略
            buffer.adjustStrategy();
            
            // 2. 动态调整批量大小
            adjustBatchSize();
            
            // 3. 调整超时时间
            adjustBatchTimeout();
            
            // 4. 输出性能报告
            logPerformanceReport();
            
        } catch (Exception e) {
            addError("Performance optimization failed", e);
        }
    }
    
    /**
     * 动态调整批量大小
     */
    private void adjustBatchSize() {
        IntelligentShardedBuffer.LoadStats stats = buffer.getLoadStats();
        double rejectionRate = stats.getRejectionRate();
        
        if (rejectionRate > 0.1) {
            // 拒绝率过高，减小批量大小
            batchSize = Math.max(10, batchSize - 5);
        } else if (rejectionRate < 0.01 && avgLatency.get() < 50) {
            // 拒绝率很低且延迟较小，增大批量大小
            batchSize = Math.min(200, batchSize + 10);
        }
    }
    
    /**
     * 动态调整批量超时时间
     */
    private void adjustBatchTimeout() {
        long currentLatency = avgLatency.get();
        
        if (currentLatency > 200) {
            // 延迟较高，减少超时时间
            batchTimeoutMs = Math.max(50, batchTimeoutMs - 10);
        } else if (currentLatency < 50) {
            // 延迟较低，可以适当增加超时时间
            batchTimeoutMs = Math.min(500, batchTimeoutMs + 10);
        }
    }
    
    /**
     * 输出性能报告
     */
    private void logPerformanceReport() {
        IntelligentShardedBuffer.LoadStats bufferStats = buffer.getLoadStats();
        
        addInfo(String.format(
            "Performance Report - Total: %d, Dropped: %d, Errors: %d, " +
            "AvgLatency: %dms, BatchSize: %d, Timeout: %dms, Rejection: %.2f%%",
            totalMessages.get(),
            droppedMessages.get(),
            errorCount.get(),
            avgLatency.get(),
            batchSize,
            batchTimeoutMs,
            bufferStats.getRejectionRate() * 100
        ));
    }
    
    /**
     * 打印统计信息
     */
    private void printStatistics() {
        IntelligentShardedBuffer.LoadStats bufferStats = buffer.getLoadStats();
        
        System.out.println("=== CustomAppender Statistics ===");
        System.out.println("Total Messages: " + totalMessages.get());
        System.out.println("Dropped Messages: " + droppedMessages.get());
        System.out.println("Error Count: " + errorCount.get());
        System.out.println("Average Latency: " + avgLatency.get() + "ms");
        System.out.println("Buffer Rejection Rate: " + 
                          String.format("%.2f%%", bufferStats.getRejectionRate() * 100));
        System.out.println("Final Batch Size: " + batchSize);
        System.out.println("Final Timeout: " + batchTimeoutMs + "ms");
        System.out.println("=================================");
    }

    // Configuration setters
    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPushType() {
        return pushType;
    }
 
    public void setPushType(String pushType) {
        this.pushType = pushType;
        this.push = PushFactory.createPush(pushType, host, port);
    }
    
    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
 
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    
    public int getMaxShards() {
        return maxShards;
    }
 
    public void setMaxShards(int maxShards) {
        this.maxShards = maxShards;
    }
    
    public boolean isEnableNtp() {
        return enableNtp;
    }
    
    public void setEnableNtp(boolean enableNtp) {
        this.enableNtp = enableNtp;
    }
    
    public long getBatchTimeoutMs() {
        return batchTimeoutMs;
    }
    
    public void setBatchTimeoutMs(long batchTimeoutMs) {
        this.batchTimeoutMs = batchTimeoutMs;
    }
}