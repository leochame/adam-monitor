package com.adam.trace;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * AdamTraceContext性能测试
 * 测试高并发场景下的性能表现
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class AdamTraceContextPerformanceTest {

    private static final int THREAD_COUNT = 50;
    private static final int OPERATIONS_PER_THREAD = 1000;
    private static final int TOTAL_OPERATIONS = THREAD_COUNT * OPERATIONS_PER_THREAD;

    @Before
    public void setUp() {
        AdamTraceContext.clearAid();
    }

    @After
    public void tearDown() {
        AdamTraceContext.clearAid();
    }

    /**
     * 测试基本操作的性能
     */
    @Test
    public void testBasicOperationsPerformance() {
        System.out.println("=== 基本操作性能测试 ===");
        
        // 测试setAid性能
        long setStartTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            AdamTraceContext.setAid("test-aid-" + i);
        }
        long setDuration = System.nanoTime() - setStartTime;
        System.out.println("setAid 10000次耗时: " + (setDuration / 1_000_000) + "ms");
        
        // 测试getAid性能
        AdamTraceContext.setAid("performance-test-aid");
        long getStartTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            AdamTraceContext.getAid();
        }
        long getDuration = System.nanoTime() - getStartTime;
        System.out.println("getAid 10000次耗时: " + (getDuration / 1_000_000) + "ms");
        
        // 测试injectContext性能
        long injectStartTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            AdamTraceContext.injectContext();
        }
        long injectDuration = System.nanoTime() - injectStartTime;
        System.out.println("injectContext 10000次耗时: " + (injectDuration / 1_000_000) + "ms");
        
        // 验证性能要求（每个操作应该在合理时间内完成）
        assertTrue("setAid操作应该足够快", setDuration < 1_000_000_000L); // 1秒
        assertTrue("getAid操作应该足够快", getDuration < 500_000_000L); // 0.5秒
        assertTrue("injectContext操作应该足够快", injectDuration < 1_000_000_000L); // 1秒
    }

    /**
     * 测试高并发场景下的性能和正确性
     */
    @Test
    public void testHighConcurrencyPerformance() throws InterruptedException {
        System.out.println("=== 高并发性能测试 ===");
        System.out.println("线程数: " + THREAD_COUNT);
        System.out.println("每线程操作数: " + OPERATIONS_PER_THREAD);
        System.out.println("总操作数: " + TOTAL_OPERATIONS);
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalDuration = new AtomicLong(0);
        
        // 创建并发任务
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    
                    long threadStartTime = System.nanoTime();
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        try {
                            String aid = "thread-" + threadId + "-op-" + j;
                            
                            // 执行完整的操作流程
                            AdamTraceContext.setAid(aid);
                            String retrievedAid = AdamTraceContext.getAid();
                            
                            if (aid.equals(retrievedAid)) {
                                var context = AdamTraceContext.injectContext();
                                if (!context.isEmpty()) {
                                    AdamTraceContext.extractContext(context);
                                    successCount.incrementAndGet();
                                } else {
                                    errorCount.incrementAndGet();
                                }
                            } else {
                                errorCount.incrementAndGet();
                            }
                            
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        }
                    }
                    
                    long threadDuration = System.nanoTime() - threadStartTime;
                    totalDuration.addAndGet(threadDuration);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        long testStartTime = System.nanoTime();
        startLatch.countDown(); // 开始测试
        
        boolean completed = endLatch.await(60, TimeUnit.SECONDS);
        long testDuration = System.nanoTime() - testStartTime;
        
        executor.shutdown();
        
        // 输出测试结果
        System.out.println("测试完成: " + completed);
        System.out.println("总耗时: " + (testDuration / 1_000_000) + "ms");
        System.out.println("成功操作数: " + successCount.get());
        System.out.println("失败操作数: " + errorCount.get());
        System.out.println("平均每操作耗时: " + (totalDuration.get() / TOTAL_OPERATIONS / 1000) + "μs");
        System.out.println("吞吐量: " + (TOTAL_OPERATIONS * 1_000_000_000L / testDuration) + " ops/s");
        
        // 验证测试结果
        assertTrue("测试应该在规定时间内完成", completed);
        assertTrue("成功率应该大于95%", successCount.get() > TOTAL_OPERATIONS * 0.95);
        assertTrue("错误率应该小于5%", errorCount.get() < TOTAL_OPERATIONS * 0.05);
    }

    /**
     * 测试内存使用情况
     */
    @Test
    public void testMemoryUsage() {
        System.out.println("=== 内存使用测试 ===");
        
        Runtime runtime = Runtime.getRuntime();
        
        // 强制垃圾回收，获取基准内存
        System.gc();
        Thread.yield();
        long baseMemory = runtime.totalMemory() - runtime.freeMemory();
        
        System.out.println("基准内存使用: " + (baseMemory / 1024 / 1024) + "MB");
        
        // 执行大量操作
        List<String> aids = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            String aid = "memory-test-aid-" + i;
            aids.add(aid);
            
            AdamTraceContext.setAid(aid);
            AdamTraceContext.getAid();
            AdamTraceContext.injectContext();
            
            if (i % 1000 == 0) {
                AdamTraceContext.clearAid();
            }
        }
        
        // 检查内存使用
        long afterOperationsMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = afterOperationsMemory - baseMemory;
        
        System.out.println("操作后内存使用: " + (afterOperationsMemory / 1024 / 1024) + "MB");
        System.out.println("内存增长: " + (memoryIncrease / 1024 / 1024) + "MB");
        
        // 清理并再次检查
        AdamTraceContext.clearAid();
        aids.clear();
        System.gc();
        Thread.yield();
        
        long afterCleanupMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("清理后内存使用: " + (afterCleanupMemory / 1024 / 1024) + "MB");
        
        // 验证没有严重的内存泄漏
        long finalIncrease = afterCleanupMemory - baseMemory;
        System.out.println("最终内存增长: " + (finalIncrease / 1024 / 1024) + "MB");
        
        // 内存增长应该在合理范围内（小于50MB）
        assertTrue("内存增长应该在合理范围内", finalIncrease < 50 * 1024 * 1024);
    }

    /**
     * 测试线程安全性
     */
    @Test
    public void testThreadSafety() throws InterruptedException {
        System.out.println("=== 线程安全性测试 ===");
        
        int threadCount = 20;
        int operationsPerThread = 500;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        AtomicInteger conflictCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        String expectedAid = "thread-" + threadId + "-aid-" + j;
                        
                        AdamTraceContext.setAid(expectedAid);
                        
                        // 短暂等待，增加并发冲突的可能性
                        if (j % 10 == 0) {
                            Thread.yield();
                        }
                        
                        String actualAid = AdamTraceContext.getAid();
                        
                        // 检查是否发生了意外的AID混乱
                        if (!expectedAid.equals(actualAid)) {
                            conflictCount.incrementAndGet();
                            System.err.println("线程安全冲突: 期望=" + expectedAid + ", 实际=" + actualAid);
                        }
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        
        executor.shutdown();
        
        System.out.println("线程安全测试完成: " + completed);
        System.out.println("检测到的冲突数: " + conflictCount.get());
        
        assertTrue("测试应该完成", completed);
        // 由于使用ThreadLocal，不应该有冲突
        assertEquals("不应该有线程安全冲突", 0, conflictCount.get());
    }

    /**
     * 测试极限并发场景
     */
    @Test
    public void testExtremeConcurrency() throws InterruptedException {
        System.out.println("=== 极限并发测试 ===");
        
        int extremeThreadCount = 100;
        int quickOperations = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(extremeThreadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(extremeThreadCount);
        
        AtomicInteger completedThreads = new AtomicInteger(0);
        AtomicLong totalOperations = new AtomicLong(0);
        
        long testStartTime = System.nanoTime();
        
        for (int i = 0; i < extremeThreadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < quickOperations; j++) {
                        AdamTraceContext.setAid("extreme-" + threadId + "-" + j);
                        AdamTraceContext.getAid();
                        AdamTraceContext.injectContext();
                        totalOperations.incrementAndGet();
                    }
                    
                    completedThreads.incrementAndGet();
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        boolean completed = endLatch.await(45, TimeUnit.SECONDS);
        long testDuration = System.nanoTime() - testStartTime;
        
        executor.shutdown();
        
        System.out.println("极限并发测试完成: " + completed);
        System.out.println("完成的线程数: " + completedThreads.get() + "/" + extremeThreadCount);
        System.out.println("总操作数: " + totalOperations.get());
        System.out.println("总耗时: " + (testDuration / 1_000_000) + "ms");
        
        if (totalOperations.get() > 0) {
            System.out.println("极限吞吐量: " + (totalOperations.get() * 1_000_000_000L / testDuration) + " ops/s");
        }
        
        assertTrue("极限并发测试应该完成", completed);
        assertTrue("大部分线程应该成功完成", completedThreads.get() > extremeThreadCount * 0.9);
    }
}