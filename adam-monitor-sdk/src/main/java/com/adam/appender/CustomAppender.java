package com.adam.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.adam.entitys.LogMessage;
import com.adam.push.IPush;
import com.adam.push.PushFactory;
import com.adam.utils.NTPClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

// 日志采集
public class CustomAppender<E> extends UnsynchronizedAppenderBase<E> {

    private String systemName;
    private String groupId;
    private String host;
    private int port;
    private IPush push;

    // 优化参数
    private int queueCapacity = 10000;
    private int batchSize = 5;
    private int maxShards = 8;

    private ShardedBuffer buffer;
    private ExecutorService sendExecutor;
    private volatile boolean running = true;

    @Override
    public void start() {
        this.buffer = new ShardedBuffer(maxShards, queueCapacity);
        this.sendExecutor = Executors.newFixedThreadPool(maxShards);

        for (int i = 0; i < maxShards; i++) {
            sendExecutor.submit(this::batchSendTask);
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
        // TODO 待解决的问题
//        if (!className.startsWith(groupId)) return;

        LogMessage log = new LogMessage(
                systemName,
                className,
                callerData[0].getMethodName(),
                event.getFormattedMessage(),
                NTPClient.getNetworkTime()
//                10L
        );

        if (!buffer.offer(log)) {
            handleBackpressure(log);
        }
    }

    private void batchSendTask() {
        List<LogMessage> batch = new ArrayList<>(batchSize);
        while (running) {
            try {
                LogMessage msg = buffer.poll(100, TimeUnit.MILLISECONDS);
                if (msg != null) {
                    batch.add(msg);
                    if (batch.size() >= batchSize) {
                        sendBatch(batch);
                        batch.clear();
                    }
                } else if (!batch.isEmpty()) {
                    sendBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void sendBatch(List<LogMessage> batch) {
        try {
            push.sendBatch(batch);
        } catch (Exception e) {

            e.printStackTrace();
            throw new RuntimeException(e);
            //TODO  等待删除
//            handleSendError(batch, e);
        }
    }

    private void handleBackpressure(LogMessage log) {
        // 实现降级策略：写入本地文件
//        LocalStorage.write(log);
    }

    private void handleSendError(List<LogMessage> failedBatch, Exception e) {
        return;
    }

    @Override
    public void stop() {
        running = false;
        sendExecutor.shutdownNow();
        push.close();
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

    // Configuration setters
    public  void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public void setPushType(String pushType) {
        this.push = PushFactory.createPush(pushType, host, port);
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public void setMaxShards(int maxShards) {
        this.maxShards = maxShards;
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
}