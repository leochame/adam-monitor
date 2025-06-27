package com.adam.trace;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * AdamTraceContext集成测试
 * 测试在Spring Boot环境下的AID透传功能
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class AdamTraceContextIntegrationTest {

    @Before
    public void setUp() {
        AdamTraceContext.clearAid();
    }

    @After
    public void tearDown() {
        AdamTraceContext.clearAid();
    }

    /**
     * 测试HTTP头部处理功能
     */
    @Test
    public void testHttpHeaderHandling() {
        String testAid = "http-test-aid-123";
        
        // 模拟HTTP请求头处理
        HttpHeaders headers = new HttpHeaders();
        headers.set(AdamTraceContext.getAidHeaderKey(), testAid);
        
        // 验证头部键名正确
        assertEquals("Header键名应该正确", "X-Adam-AID", AdamTraceContext.getAidHeaderKey());
        
        // 验证能正确获取头部值
        String headerValue = headers.getFirst(AdamTraceContext.getAidHeaderKey());
        assertEquals("头部值应该正确", testAid, headerValue);
    }

    /**
     * 测试多线程环境下的AID隔离性
     */
    @Test
    public void testMultiThreadAidIsolation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        try {
            // 创建多个并发任务
            CompletableFuture<Void>[] futures = new CompletableFuture[10];
            
            for (int i = 0; i < 10; i++) {
                final int taskId = i;
                futures[i] = CompletableFuture.runAsync(() -> {
                    String taskAid = "task-" + taskId + "-aid";
                    
                    // 设置当前任务的AID
                    AdamTraceContext.setAid(taskAid);
                    
                    try {
                        // 模拟业务处理时间
                        Thread.sleep(100);
                        
                        // 验证AID没有被其他线程影响
                        String currentAid = AdamTraceContext.getAid();
                        assertEquals("任务" + taskId + "的AID应该保持不变", taskAid, currentAid);
                        
                        // 测试上下文注入和提取
                        var context = AdamTraceContext.injectContext();
                        assertFalse("上下文不应该为空", context.isEmpty());
                        assertEquals("注入的AID应该正确", taskAid, context.get(AdamTraceContext.getAidHeaderKey()));
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } finally {
                        // 清理当前线程的AID
                        AdamTraceContext.clearAid();
                    }
                }, executor);
            }
            
            // 等待所有任务完成
            CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
            
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    /**
     * 测试异步处理中的AID传播
     */
    @Test
    public void testAsyncProcessingAidPropagation() throws Exception {
        String originalAid = "async-processing-aid";
        AdamTraceContext.setAid(originalAid);
        
        // 模拟异步处理前保存上下文
        var savedContext = AdamTraceContext.injectContext();
        
        // 清除当前线程的AID，模拟跨线程场景
        AdamTraceContext.clearAid();
        assertNull("清除后AID应该为null", AdamTraceContext.getAid());
        
        // 异步处理
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            // 在异步线程中恢复上下文
            AdamTraceContext.extractContext(savedContext);
            
            // 验证AID是否正确恢复
            return AdamTraceContext.getAid();
        });
        
        String asyncAid = future.get(5, TimeUnit.SECONDS);
        assertEquals("异步处理中应该能正确获取AID", originalAid, asyncAid);
    }

    /**
     * 测试嵌套调用中的AID传播
     */
    @Test
    public void testNestedCallAidPropagation() {
        String level1Aid = "level1-aid";
        AdamTraceContext.setAid(level1Aid);
        
        // 第一层调用
        String result1 = simulateServiceCall(() -> {
            assertEquals("第一层应该获取到正确的AID", level1Aid, AdamTraceContext.getAid());
            
            // 第二层调用，设置新的AID
            String level2Aid = "level2-aid";
            AdamTraceContext.setAid(level2Aid);
            
            return simulateServiceCall(() -> {
                assertEquals("第二层应该获取到新的AID", level2Aid, AdamTraceContext.getAid());
                
                // 第三层调用
                String level3Aid = "level3-aid";
                AdamTraceContext.setAid(level3Aid);
                
                return simulateServiceCall(() -> {
                    assertEquals("第三层应该获取到最新的AID", level3Aid, AdamTraceContext.getAid());
                    return "level3-result";
                });
            });
        });
        
        assertEquals("嵌套调用应该返回正确结果", "level3-result", result1);
    }

    /**
     * 模拟服务调用
     */
    private String simulateServiceCall(java.util.function.Supplier<String> serviceLogic) {
        // 模拟服务调用前的上下文保存
        var context = AdamTraceContext.injectContext();
        
        try {
            // 执行业务逻辑
            return serviceLogic.get();
        } finally {
            // 模拟服务调用后的上下文恢复（在某些场景下可能需要）
            // 这里不做恢复，因为我们要测试AID的覆盖行为
        }
    }

    /**
     * 测试错误场景下的AID处理
     */
    @Test
    public void testErrorScenarioAidHandling() {
        String testAid = "error-test-aid";
        AdamTraceContext.setAid(testAid);
        
        try {
            // 模拟业务异常
            simulateBusinessError();
            fail("应该抛出异常");
        } catch (RuntimeException e) {
            // 验证异常发生后AID仍然可以正常获取
            assertEquals("异常发生后AID应该仍然可用", testAid, AdamTraceContext.getAid());
        }
    }

    private void simulateBusinessError() {
        // 验证在抛异常前AID是正常的
        assertNotNull("业务异常前AID应该存在", AdamTraceContext.getAid());
        throw new RuntimeException("模拟业务异常");
    }

    /**
     * 测试长时间运行任务中的AID保持
     */
    @Test
    public void testLongRunningTaskAidPersistence() throws InterruptedException {
        String longTaskAid = "long-task-aid";
        AdamTraceContext.setAid(longTaskAid);
        
        // 模拟长时间运行的任务
        for (int i = 0; i < 10; i++) {
            Thread.sleep(50); // 模拟处理时间
            
            // 验证AID在长时间运行过程中保持不变
            assertEquals("长时间运行过程中AID应该保持不变", longTaskAid, AdamTraceContext.getAid());
            
            // 模拟中间的一些操作
            var context = AdamTraceContext.injectContext();
            assertFalse("上下文注入应该成功", context.isEmpty());
        }
    }

    /**
     * 测试批量处理中的AID管理
     */
    @Test
    public void testBatchProcessingAidManagement() {
        String batchAid = "batch-processing-aid";
        
        // 模拟批量处理
        for (int i = 0; i < 5; i++) {
            // 为每个批次项目设置独立的AID
            String itemAid = batchAid + "-item-" + i;
            AdamTraceContext.setAid(itemAid);
            
            // 处理单个项目
            processBatchItem(itemAid);
            
            // 清理当前项目的AID
            AdamTraceContext.clearAid();
            assertNull("处理完成后AID应该被清理", AdamTraceContext.getAid());
        }
    }

    private void processBatchItem(String expectedAid) {
        assertEquals("批次项目AID应该正确", expectedAid, AdamTraceContext.getAid());
        
        // 模拟项目处理逻辑
        var context = AdamTraceContext.injectContext();
        assertEquals("项目处理中AID应该正确", expectedAid, context.get(AdamTraceContext.getAidHeaderKey()));
    }

    /**
     * 测试系统信息获取功能
     */
    @Test
    public void testSystemInfoRetrieval() {
        // 测试SkyWalking可用性检查
        boolean skywalkingAvailable = AdamTraceContext.isSkywalkingAvailable();
        System.out.println("SkyWalking可用性: " + skywalkingAvailable);
        
        // 测试TraceId获取
        String traceId = AdamTraceContext.getTraceId();
        System.out.println("当前TraceId: " + traceId);
        
        // 测试Header键名获取
        String headerKey = AdamTraceContext.getAidHeaderKey();
        assertNotNull("Header键名应该存在", headerKey);
        assertEquals("Header键名应该正确", "X-Adam-AID", headerKey);
        
        // 这些方法调用不应该抛出异常
        assertNotNull("SkyWalking可用性检查应该返回有效值", Boolean.valueOf(skywalkingAvailable));
    }
}