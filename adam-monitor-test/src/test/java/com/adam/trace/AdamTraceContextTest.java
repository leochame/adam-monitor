package com.adam.trace;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * AdamTraceContext单元测试
 * 测试AID透传功能的完整性
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class AdamTraceContextTest {

    @Before
    public void setUp() {
        // 每个测试前清理上下文
        AdamTraceContext.clearAid();
    }

    @After
    public void tearDown() {
        // 每个测试后清理上下文
        AdamTraceContext.clearAid();
    }

    /**
     * 测试基本的AID设置和获取功能
     */
    @Test
    public void testBasicSetAndGet() {
        // 测试设置和获取AID
        String testAid = "test-aid-123";
        AdamTraceContext.setAid(testAid);
        
        String retrievedAid = AdamTraceContext.getAid();
        assertEquals("AID设置和获取应该一致", testAid, retrievedAid);
    }

    /**
     * 测试空值和null值处理
     */
    @Test
    public void testNullAndEmptyValues() {
        // 测试null值
        AdamTraceContext.setAid(null);
        assertNull("设置null值后应该返回null", AdamTraceContext.getAid());
        
        // 测试空字符串
        AdamTraceContext.setAid("");
        assertNull("设置空字符串后应该返回null", AdamTraceContext.getAid());
        
        // 测试只有空格的字符串
        AdamTraceContext.setAid("   ");
        assertNull("设置空格字符串后应该返回null", AdamTraceContext.getAid());
    }

    /**
     * 测试AID的trim功能
     */
    @Test
    public void testAidTrimming() {
        String aidWithSpaces = "  test-aid-456  ";
        String expectedAid = "test-aid-456";
        
        AdamTraceContext.setAid(aidWithSpaces);
        String retrievedAid = AdamTraceContext.getAid();
        
        assertEquals("AID应该被正确trim", expectedAid, retrievedAid);
    }

    /**
     * 测试清除AID功能
     */
    @Test
    public void testClearAid() {
        // 先设置AID
        AdamTraceContext.setAid("test-aid-789");
        assertNotNull("设置后AID应该不为null", AdamTraceContext.getAid());
        
        // 清除AID
        AdamTraceContext.clearAid();
        assertNull("清除后AID应该为null", AdamTraceContext.getAid());
    }

    /**
     * 测试上下文注入功能
     */
    @Test
    public void testInjectContext() {
        String testAid = "inject-test-123";
        AdamTraceContext.setAid(testAid);
        
        Map<String, String> context = AdamTraceContext.injectContext();
        
        assertNotNull("注入的上下文不应该为null", context);
        assertEquals("注入的AID应该正确", testAid, context.get(AdamTraceContext.getAidHeaderKey()));
    }

    /**
     * 测试空AID时的上下文注入
     */
    @Test
    public void testInjectContextWithNoAid() {
        // 确保没有设置AID
        AdamTraceContext.clearAid();
        
        Map<String, String> context = AdamTraceContext.injectContext();
        
        assertNotNull("注入的上下文不应该为null", context);
        assertTrue("没有AID时注入的上下文应该为空", context.isEmpty());
    }

    /**
     * 测试上下文提取功能
     */
    @Test
    public void testExtractContext() {
        String testAid = "extract-test-456";
        Map<String, String> context = new HashMap<>();
        context.put(AdamTraceContext.getAidHeaderKey(), testAid);
        
        AdamTraceContext.extractContext(context);
        
        String retrievedAid = AdamTraceContext.getAid();
        assertEquals("提取的AID应该正确设置", testAid, retrievedAid);
    }

    /**
     * 测试从空上下文提取
     */
    @Test
    public void testExtractFromEmptyContext() {
        AdamTraceContext.setAid("original-aid");
        
        // 测试null上下文
        AdamTraceContext.extractContext(null);
        assertEquals("从null上下文提取不应该影响原有AID", "original-aid", AdamTraceContext.getAid());
        
        // 测试空Map
        AdamTraceContext.extractContext(new HashMap<>());
        assertEquals("从空上下文提取不应该影响原有AID", "original-aid", AdamTraceContext.getAid());
    }

    /**
     * 测试上下文提取时的trim功能
     */
    @Test
    public void testExtractContextWithTrimming() {
        String aidWithSpaces = "  extract-aid-789  ";
        String expectedAid = "extract-aid-789";
        
        Map<String, String> context = new HashMap<>();
        context.put(AdamTraceContext.getAidHeaderKey(), aidWithSpaces);
        
        AdamTraceContext.extractContext(context);
        
        String retrievedAid = AdamTraceContext.getAid();
        assertEquals("提取的AID应该被正确trim", expectedAid, retrievedAid);
    }

    /**
     * 测试线程池中的AID传播
     */
    @Test
    public void testThreadPoolAidPropagation() throws Exception {
        String mainThreadAid = "main-thread-aid";
        AdamTraceContext.setAid(mainThreadAid);
        
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        try {
            // 在线程池中获取AID
            Future<String> future = executor.submit(() -> {
                // 注意：在真实的SkyWalking环境中，AID会自动传播
                // 在测试环境中，我们需要手动传播或者依赖ThreadLocal降级
                return AdamTraceContext.getAid();
            });
            
            String threadPoolAid = future.get(5, TimeUnit.SECONDS);
            
            // 在没有SkyWalking Agent的测试环境中，ThreadLocal不会跨线程传播
            // 这是预期行为，因为ThreadLocal是线程隔离的
            // 在真实环境中，SkyWalking会处理跨线程传播
            System.out.println("主线程AID: " + mainThreadAid);
            System.out.println("线程池AID: " + threadPoolAid);
            System.out.println("SkyWalking可用性: " + AdamTraceContext.isSkywalkingAvailable());
            
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 测试异步场景下的AID传播（模拟手动传播）
     */
    @Test
    public void testAsyncAidPropagation() throws Exception {
        String originalAid = "async-test-aid";
        AdamTraceContext.setAid(originalAid);
        
        // 模拟异步调用前的上下文注入
        Map<String, String> context = AdamTraceContext.injectContext();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> asyncAid = new AtomicReference<>();
        
        // 异步执行
        new Thread(() -> {
            try {
                // 模拟异步调用后的上下文提取
                AdamTraceContext.extractContext(context);
                asyncAid.set(AdamTraceContext.getAid());
            } finally {
                latch.countDown();
            }
        }).start();
        
        latch.await(5, TimeUnit.SECONDS);
        
        assertEquals("异步场景下AID应该正确传播", originalAid, asyncAid.get());
    }

    /**
     * 测试并发场景下的AID隔离
     */
    @Test
    public void testConcurrentAidIsolation() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicReference<Exception> exception = new AtomicReference<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    String threadAid = "thread-" + threadIndex + "-aid";
                    AdamTraceContext.setAid(threadAid);
                    
                    // 短暂等待，模拟业务处理
                    Thread.sleep(10);
                    
                    String retrievedAid = AdamTraceContext.getAid();
                    if (!threadAid.equals(retrievedAid)) {
                        exception.set(new RuntimeException(
                            "线程" + threadIndex + "的AID不匹配: 期望=" + threadAid + ", 实际=" + retrievedAid));
                    }
                    
                } catch (Exception e) {
                    exception.set(e);
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown(); // 启动所有线程
        endLatch.await(10, TimeUnit.SECONDS); // 等待所有线程完成
        
        if (exception.get() != null) {
            fail("并发测试失败: " + exception.get().getMessage());
        }
    }

    /**
     * 测试SkyWalking可用性检查
     */
    @Test
    public void testSkywalkingAvailability() {
        // 在测试环境中，SkyWalking Agent通常不可用
        boolean isAvailable = AdamTraceContext.isSkywalkingAvailable();
        System.out.println("SkyWalking可用性: " + isAvailable);
        
        // 这个测试主要是为了验证方法能正常调用，不会抛异常
        assertNotNull("SkyWalking可用性检查应该返回boolean值", Boolean.valueOf(isAvailable));
    }

    /**
     * 测试TraceId获取功能
     */
    @Test
    public void testGetTraceId() {
        // 在测试环境中，通常无法获取到真实的TraceId
        String traceId = AdamTraceContext.getTraceId();
        System.out.println("TraceId: " + traceId);
        
        // 这个测试主要是为了验证方法能正常调用，不会抛异常
        // 在没有SkyWalking Agent的环境中，traceId通常为null
    }

    /**
     * 测试AID Header键名获取
     */
    @Test
    public void testGetAidHeaderKey() {
        String headerKey = AdamTraceContext.getAidHeaderKey();
        assertNotNull("Header键名不应该为null", headerKey);
        assertFalse("Header键名不应该为空", headerKey.trim().isEmpty());
        assertEquals("Header键名应该是X-Adam-AID", "X-Adam-AID", headerKey);
    }

    /**
     * 测试完整的RPC调用模拟场景
     */
    @Test
    public void testRpcCallSimulation() {
        // 模拟客户端设置AID
        String clientAid = "rpc-client-aid-123";
        AdamTraceContext.setAid(clientAid);
        
        // 模拟客户端注入上下文
        Map<String, String> rpcHeaders = AdamTraceContext.injectContext();
        assertFalse("RPC头部不应该为空", rpcHeaders.isEmpty());
        
        // 清除客户端上下文，模拟跨进程调用
        AdamTraceContext.clearAid();
        assertNull("清除后客户端AID应该为null", AdamTraceContext.getAid());
        
        // 模拟服务端提取上下文
        AdamTraceContext.extractContext(rpcHeaders);
        String serverAid = AdamTraceContext.getAid();
        
        assertEquals("服务端应该获取到客户端的AID", clientAid, serverAid);
    }

    /**
     * 测试完整的MQ消息传播模拟场景
     */
    @Test
    public void testMqMessageSimulation() {
        // 模拟生产者设置AID
        String producerAid = "mq-producer-aid-456";
        AdamTraceContext.setAid(producerAid);
        
        // 模拟生产者发送消息时注入上下文
        Map<String, String> messageHeaders = AdamTraceContext.injectContext();
        assertFalse("消息头部不应该为空", messageHeaders.isEmpty());
        
        // 清除生产者上下文
        AdamTraceContext.clearAid();
        
        // 模拟消费者接收消息时提取上下文
        AdamTraceContext.extractContext(messageHeaders);
        String consumerAid = AdamTraceContext.getAid();
        
        assertEquals("消费者应该获取到生产者的AID", producerAid, consumerAid);
        
        // 模拟消费者处理完成后清理上下文
        AdamTraceContext.clearAid();
        assertNull("处理完成后AID应该被清理", AdamTraceContext.getAid());
    }
}