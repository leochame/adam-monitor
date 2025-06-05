package com.adam.appender;

import com.adam.entitys.LogMessage;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 智能分片缓冲区
 * 特性：
 * 1. 基于负载的智能分片
 * 2. 动态负载均衡
 * 3. 背压检测与处理
 * 4. 性能监控
 */
public class IntelligentShardedBuffer {
    
    private final int shardCount;
    private final BlockingQueue<LogMessage>[] shards;
    private final AtomicLong[] shardLoads;
    private final AtomicInteger[] shardSizes;
    private final int capacity;
    
    // 负载均衡策略
    private volatile LoadBalanceStrategy strategy = LoadBalanceStrategy.LEAST_LOADED;
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    
    // 性能监控
    private final AtomicLong totalOffers = new AtomicLong(0);
    private final AtomicLong totalPolls = new AtomicLong(0);
    private final AtomicLong rejectedOffers = new AtomicLong(0);
    
    public enum LoadBalanceStrategy {
        ROUND_ROBIN,    // 轮询
        LEAST_LOADED,   // 最少负载
        HASH_BASED      // 基于哈希
    }
    
    @SuppressWarnings("unchecked")
    public IntelligentShardedBuffer(int shardCount, int capacity) {
        this.shardCount = shardCount;
        this.capacity = capacity;
        this.shards = new BlockingQueue[shardCount];
        this.shardLoads = new AtomicLong[shardCount];
        this.shardSizes = new AtomicInteger[shardCount];
        
        for (int i = 0; i < shardCount; i++) {
            this.shards[i] = new LinkedBlockingQueue<>(capacity / shardCount);
            this.shardLoads[i] = new AtomicLong(0);
            this.shardSizes[i] = new AtomicInteger(0);
        }
    }
    
    /**
     * 智能投递消息
     */
    public boolean offer(LogMessage message) {
        totalOffers.incrementAndGet();
        
        int targetShard = selectShard(message);
        boolean success = shards[targetShard].offer(message);
        
        if (success) {
            shardSizes[targetShard].incrementAndGet();
            shardLoads[targetShard].incrementAndGet();
        } else {
            rejectedOffers.incrementAndGet();
            // 尝试其他分片
            success = tryOtherShards(message, targetShard);
        }
        
        return success;
    }
    
    /**
     * 强制投递（可能阻塞）
     */
    public boolean forceOffer(LogMessage message, long timeout, TimeUnit unit) 
            throws InterruptedException {
        int targetShard = selectShard(message);
        boolean success = shards[targetShard].offer(message, timeout, unit);
        
        if (success) {
            shardSizes[targetShard].incrementAndGet();
            shardLoads[targetShard].incrementAndGet();
        }
        
        return success;
    }
    
    /**
     * 从指定分片获取消息
     */
    public LogMessage poll(int shardId, long timeout, TimeUnit unit) 
            throws InterruptedException {
        if (shardId < 0 || shardId >= shardCount) {
            throw new IllegalArgumentException("Invalid shard ID: " + shardId);
        }
        
        LogMessage message = shards[shardId].poll(timeout, unit);
        if (message != null) {
            totalPolls.incrementAndGet();
            shardSizes[shardId].decrementAndGet();
        }
        
        return message;
    }
    
    /**
     * 重试投递失败的批次
     */
    public void retryOfferAll(List<LogMessage> messages) {
        for (LogMessage message : messages) {
            // 使用最少负载策略重新分配
            int targetShard = findLeastLoadedShard();
            if (!shards[targetShard].offer(message)) {
                // 如果仍然失败，记录警告
                System.err.println("Failed to retry offer message: " + message.getContent());
            } else {
                shardSizes[targetShard].incrementAndGet();
                shardLoads[targetShard].incrementAndGet();
            }
        }
    }
    
    /**
     * 排空剩余消息
     */
    public List<LogMessage> drainRemaining() {
        List<LogMessage> remaining = new ArrayList<>();
        
        for (BlockingQueue<LogMessage> shard : shards) {
            LogMessage message;
            while ((message = shard.poll()) != null) {
                remaining.add(message);
            }
        }
        
        return remaining;
    }
    
    /**
     * 选择目标分片
     */
    private int selectShard(LogMessage message) {
        switch (strategy) {
            case ROUND_ROBIN:
                return roundRobinCounter.getAndIncrement() % shardCount;
                
            case LEAST_LOADED:
                return findLeastLoadedShard();
                
            case HASH_BASED:
                return Math.abs(message.getClassName().hashCode()) % shardCount;
                
            default:
                return 0;
        }
    }
    
    /**
     * 查找负载最少的分片
     */
    private int findLeastLoadedShard() {
        int minLoadShard = 0;
        int minSize = shardSizes[0].get();
        
        for (int i = 1; i < shardCount; i++) {
            int currentSize = shardSizes[i].get();
            if (currentSize < minSize) {
                minSize = currentSize;
                minLoadShard = i;
            }
        }
        
        return minLoadShard;
    }
    
    /**
     * 尝试其他分片
     */
    private boolean tryOtherShards(LogMessage message, int excludeShard) {
        for (int i = 0; i < shardCount; i++) {
            if (i != excludeShard && shards[i].offer(message)) {
                shardSizes[i].incrementAndGet();
                shardLoads[i].incrementAndGet();
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取负载统计
     */
    public LoadStats getLoadStats() {
        int[] sizes = new int[shardCount];
        long[] loads = new long[shardCount];
        
        for (int i = 0; i < shardCount; i++) {
            sizes[i] = shardSizes[i].get();
            loads[i] = shardLoads[i].get();
        }
        
        return new LoadStats(sizes, loads, totalOffers.get(), 
                           totalPolls.get(), rejectedOffers.get());
    }
    
    /**
     * 动态调整负载均衡策略
     */
    public void adjustStrategy() {
        LoadStats stats = getLoadStats();
        double loadVariance = calculateLoadVariance(stats.shardSizes);
        
        // 如果负载不均衡，切换到最少负载策略
        if (loadVariance > capacity * 0.1) {
            strategy = LoadBalanceStrategy.LEAST_LOADED;
        } else {
            strategy = LoadBalanceStrategy.ROUND_ROBIN;
        }
    }
    
    /**
     * 计算负载方差
     */
    private double calculateLoadVariance(int[] sizes) {
        double mean = Arrays.stream(sizes).average().orElse(0.0);
        double variance = Arrays.stream(sizes)
                .mapToDouble(size -> Math.pow(size - mean, 2))
                .average().orElse(0.0);
        return Math.sqrt(variance);
    }
    
    /**
     * 负载统计信息
     */
    public static class LoadStats {
        public final int[] shardSizes;
        public final long[] shardLoads;
        public final long totalOffers;
        public final long totalPolls;
        public final long rejectedOffers;
        
        public LoadStats(int[] shardSizes, long[] shardLoads, 
                        long totalOffers, long totalPolls, long rejectedOffers) {
            this.shardSizes = shardSizes;
            this.shardLoads = shardLoads;
            this.totalOffers = totalOffers;
            this.totalPolls = totalPolls;
            this.rejectedOffers = rejectedOffers;
        }
        
        public double getRejectionRate() {
            return totalOffers > 0 ? (double) rejectedOffers / totalOffers : 0.0;
        }
        
        public double getThroughput() {
            return totalPolls;
        }
        
        public int getTotalSize() {
            return Arrays.stream(shardSizes).sum();
        }
    }
}