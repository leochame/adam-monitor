package com.adam.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.adam.entitys.LogMessage;
import com.adam.push.IPush;
import com.adam.push.PushFactory;
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
                System.currentTimeMillis() // 使用系统当前时间替代NTP时间
        );
        
        // 设置跟踪ID，可以使用MDC中的traceId或生成一个UUID
        String traceId = event.getMDCPropertyMap().get("traceId");
        log.setTraceId(traceId != null ? traceId : java.util.UUID.randomUUID().toString());

        if (!buffer.offer(log)) {
            handleBackpressure(log);
        }
    }

    private void batchSendTask() {
        List<LogMessage> batch = new ArrayList<>(batchSize);
        while (running) {
            try {
                LogMessage msg = buffer.poll(1000, TimeUnit.MILLISECONDS);
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
            // 不抛出异常，而是调用错误处理方法
            handleSendError(batch, e);
        }
    }

    private void handleBackpressure(LogMessage log) {
        // 实现降级策略：记录丢弃的日志并输出警告
        System.err.println("队列已满，日志被丢弃: " + log.getClassName() + "." + log.getMethodName());
        // 可以考虑实现本地文件存储作为备份
        // 或者尝试重新放入队列，但有可能导致阻塞
        try {
            // 尝试再次放入队列，最多等待100ms
            boolean offered = false;
            for (int i = 0; i < 3 && !offered; i++) {
                offered = buffer.offer(log);
                if (!offered) {
                    Thread.sleep(50);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleSendError(List<LogMessage> failedBatch, Exception e) {
        // 记录错误信息
        System.err.println("发送日志批次失败: " + e.getMessage());
        e.printStackTrace();
        
        // 尝试重新放入队列，以便下次重试
        try {
            // 避免无限循环重试导致的资源耗尽
            if (failedBatch.size() <= batchSize / 2) {
                buffer.retryOfferAll(failedBatch);
                System.out.println("已将" + failedBatch.size() + "条失败日志重新放入队列");
            } else {
                System.err.println("失败批次过大，放弃重试: " + failedBatch.size() + "条日志");
            }
        } catch (Exception retryEx) {
            System.err.println("重新放入队列失败: " + retryEx.getMessage());
        }
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
        // 简化SDK，统一使用Kafka作为推送类型
        this.push = PushFactory.createPush("kafka", host, port);
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