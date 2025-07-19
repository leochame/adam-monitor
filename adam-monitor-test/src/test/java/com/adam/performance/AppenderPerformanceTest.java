package com.adam.performance;

import com.adam.appender.CustomAppender;
import com.adam.trace.AdamTraceContext;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * CustomAppender性能测试
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class AppenderPerformanceTest {

    private static final int THREAD_COUNT = 10;
    private static final int MESSAGES_PER_THREAD = 1000;
    private static final int TOTAL_MESSAGES = THREAD_COUNT * MESSAGES_PER_THREAD;
    
    private ExecutorService executorService;
    private LoggerContext loggerContext;

    @Before
    public void setUp() {
        AdamTraceContext.clearAid();
        executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        loggerContext = new LoggerContext();
    }

    @After
    public void tearDown() {
        AdamTraceContext.clearAid();
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        if (loggerContext != null) {
            loggerContext.stop();
        }
    }

    /**
     * 测试CustomAppender的基础性能
     */
    @Test
    public void testCustomAppenderBasicPerformance() throws Exception {
        System.out.println("=== 测试CustomAppender基础性能 ===");
        
        // 创建并配置CustomAppender
        CustomAppender<ILoggingEvent> appender = new CustomAppender<>();
        configureCustomAppender(appender);
        
        PerformanceResult result = runPerformanceTest(appender, "CustomAppender");
        
        // 验证性能指标
        assertTrue("吞吐量应该大于1000 msg/s", result.throughput > 1000);
        assertTrue("平均延迟应该小于50ms", result.avgLatency < 50);
        assertTrue("成功率应该大于80%", result.successRate > 0.80);
        
        System.out.println("CustomAppender基础性能测试完成");
        printPerformanceResult(result);
    }

    /**
     * 测试CustomAppender的并发性能
     */
    @Test
    public void testCustomAppenderConcurrentPerformance() throws Exception {
        System.out.println("=== 测试CustomAppender并发性能 ===");
        
        // 创建并配置CustomAppender
        CustomAppender<ILoggingEvent> appender = new CustomAppender<>();
        configureCustomAppender(appender);
        
        PerformanceResult result = runConcurrentTest(appender, "CustomAppender");
        
        System.out.println("CustomAppender并发性能测试完成");
        printPerformanceResult(result);
        
        // 并发测试应该保持合理的性能
        assertTrue("并发下成功率应该大于70%", result.successRate > 0.70);
        assertTrue("并发下平均延迟应该小于100ms", result.avgLatency < 100);
    }

    /**
     * 配置CustomAppender
     */
    private void configureCustomAppender(CustomAppender<ILoggingEvent> appender) {
        appender.setSystemName("test-system");
        appender.setGroupId("com.adam");
        appender.setHost("localhost");
        appender.setPort(9092);
        appender.setBatchSize(16384);
        appender.start();
    }

    /**
     * 运行性能测试
     */
    private PerformanceResult runPerformanceTest(ch.qos.logback.core.Appender<ILoggingEvent> appender, 
                                                String testName) throws Exception {
        return runLoadTest(appender, testName, THREAD_COUNT, MESSAGES_PER_THREAD);
    }

    /**
     * 运行并发测试
     */
    private PerformanceResult runConcurrentTest(ch.qos.logback.core.Appender<ILoggingEvent> appender, 
                                               String testName) throws Exception {
        return runLoadTest(appender, testName, THREAD_COUNT * 2, MESSAGES_PER_THREAD * 2);
    }

    /**
     * 运行负载测试
     */
    private PerformanceResult runLoadTest(ch.qos.logback.core.Appender<ILoggingEvent> appender, 
                                         String testName, int threads, int messagesPerThread) throws Exception {
        
        long startTime = System.currentTimeMillis();
        long startMemory = getUsedMemory();
        
        AtomicLong successCount = new AtomicLong(0);
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        
        // 创建任务
        CountDownLatch latch = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < messagesPerThread; j++) {
                        long sendTime = System.currentTimeMillis();
                        
                        // 创建日志事件
                        LoggingEvent event = createLogEvent("Test message from thread " + threadId + ", message " + j);
                        
                        // 发送日志
                        try {
                            appender.doAppend(event);
                            successCount.incrementAndGet();
                            latencies.add(System.currentTimeMillis() - sendTime);
                        } catch (Exception e) {
                            // 记录失败
                        }
                        
                        // 短暂延迟模拟真实场景
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有任务完成
        latch.await(60, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long endMemory = getUsedMemory();
        
        // 计算性能指标
        long testDuration = endTime - startTime;
        double throughput = (double) successCount.get() / (testDuration / 1000.0);
        
        // 计算延迟统计
        long[] latencyArray = latencies.stream().mapToLong(Long::longValue).toArray();
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        // 计算99%分位延迟
        long p99Latency = 0;
        if (latencyArray.length > 0) {
            java.util.Arrays.sort(latencyArray);
            int p99Index = (int) (latencyArray.length * 0.99);
            p99Latency = latencyArray[Math.min(p99Index, latencyArray.length - 1)];
        }
        
        double successRate = (double) successCount.get() / (threads * messagesPerThread);
        double memoryUsage = (endMemory - startMemory) / 1024.0 / 1024.0; // MB
        
        return new PerformanceResult(testName, throughput, avgLatency, p99Latency, successRate, memoryUsage, testDuration);
    }

    /**
     * 创建日志事件
     */
    private LoggingEvent createLogEvent(String message) {
        LoggingEvent event = new LoggingEvent();
        event.setMessage(message);
        event.setTimeStamp(System.currentTimeMillis());
        event.setLevel(ch.qos.logback.classic.Level.INFO);
        event.setLoggerName("com.adam.test");
        return event;
    }

    /**
     * 获取已使用内存
     */
    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * 打印性能结果
     */
    private void printPerformanceResult(PerformanceResult result) {
        System.out.println("=== 性能测试结果 ===");
        System.out.printf("测试名称: %s%n", result.testName);
        System.out.printf("吞吐量: %.2f msg/s%n", result.throughput);
        System.out.printf("平均延迟: %.2f ms%n", result.avgLatency);
        System.out.printf("99%%分位延迟: %d ms%n", result.p99Latency);
        System.out.printf("成功率: %.2f%%%n", result.successRate * 100);
        System.out.printf("内存使用: %.2f MB%n", result.memoryUsage);
        System.out.printf("测试时长: %d ms%n", result.testDuration);
        System.out.println("==================");
    }

    /**
     * 性能结果类
     */
    private static class PerformanceResult {
        final String testName;
        final double throughput;      // msg/s
        final double avgLatency;      // ms
        final long p99Latency;        // ms
        final double successRate;     // 0-1
        final double memoryUsage;     // MB
        final long testDuration;      // ms

        PerformanceResult(String testName, double throughput, double avgLatency, 
                         long p99Latency, double successRate, double memoryUsage, long testDuration) {
            this.testName = testName;
            this.throughput = throughput;
            this.avgLatency = avgLatency;
            this.p99Latency = p99Latency;
            this.successRate = successRate;
            this.memoryUsage = memoryUsage;
            this.testDuration = testDuration;
        }
    }
} 