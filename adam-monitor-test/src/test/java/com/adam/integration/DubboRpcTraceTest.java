package com.adam.integration;

import com.adam.rpc.UserService;
import com.adam.rpc.dto.OrderRequest;
import com.adam.rpc.dto.OrderResponse;
import com.adam.rpc.dto.UserInfo;
import com.adam.trace.AdamTraceContext;
import org.apache.dubbo.config.annotation.DubboReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

/**
 * Dubbo RPC集成测试 - 测试AID和TraceId在RPC调用中的透传
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dubbo")
public class DubboRpcTraceTest {

    @DubboReference(version = "1.0.0", timeout = 30000, check = false)
    private UserService userService;

    private ExecutorService executorService;

    @Before
    public void setUp() {
        AdamTraceContext.clearAid();
        executorService = Executors.newFixedThreadPool(5);
    }

    @After
    public void tearDown() {
        AdamTraceContext.clearAid();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    /**
     * 测试基本的用户查询RPC调用中的AID透传
     */
    @Test
    public void testUserQueryWithAidPropagation() {
        String originalAid = "dubbo-user-query-" + UUID.randomUUID();
        
        // 在Consumer端设置AID
        AdamTraceContext.setAid(originalAid);
        
        // 执行RPC调用
        UserInfo userInfo = userService.getUserById("user123");
        
        // 验证结果
        assertNotNull("用户信息不应该为null", userInfo);
        assertEquals("用户ID应该正确", "user123", userInfo.getUserId());
        assertEquals("AID应该正确透传", originalAid, userInfo.getAid());
        assertNotNull("TraceId应该存在", userInfo.getTraceId());
        
        System.out.println("用户查询测试完成: " + userInfo);
    }

    /**
     * 测试复杂订单创建RPC调用中的AID和TraceId透传
     */
    @Test
    public void testOrderCreationWithContextPropagation() {
        String originalAid = "dubbo-order-creation-" + UUID.randomUUID();
        
        // 在Consumer端设置AID
        AdamTraceContext.setAid(originalAid);
        
        // 构建订单请求
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setUserId("user456");
        orderRequest.setTotalAmount(new BigDecimal("199.99"));
        orderRequest.setDeliveryAddress("北京市朝阳区测试地址123号");
        
        // 添加订单项
        OrderRequest.OrderItem item1 = new OrderRequest.OrderItem();
        item1.setProductId("prod001");
        item1.setProductName("测试商品1");
        item1.setQuantity(2);
        item1.setPrice(new BigDecimal("99.99"));
        
        OrderRequest.OrderItem item2 = new OrderRequest.OrderItem();
        item2.setProductId("prod002");
        item2.setProductName("测试商品2");
        item2.setQuantity(1);
        item2.setPrice(new BigDecimal("99.99"));
        
        orderRequest.setItems(Arrays.asList(item1, item2));
        
        // 执行RPC调用
        OrderResponse response = userService.createOrder(orderRequest);
        
        // 验证结果
        assertNotNull("订单响应不应该为null", response);
        assertTrue("订单创建应该成功", response.isSuccess());
        assertNotNull("订单ID不应该为null", response.getOrderId());
        assertEquals("用户ID应该正确", "user456", response.getUserId());
        assertEquals("AID应该正确透传", originalAid, response.getAid());
        assertNotNull("TraceId应该存在", response.getTraceId());
        
        System.out.println("订单创建测试完成: " + response);
    }

    /**
     * 测试多线程并发RPC调用中的AID隔离性
     */
    @Test
    public void testConcurrentRpcCallsWithAidIsolation() throws Exception {
        int threadCount = 5;
        int callsPerThread = 3;
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ConcurrentMap<String, List<String>> aidResults = new ConcurrentHashMap<>();
        
        // 启动多个线程进行并发测试
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    
                    String threadAid = "thread-" + threadId + "-aid-" + UUID.randomUUID();
                    List<String> threadResults = new ArrayList<>();
                    
                    for (int j = 0; j < callsPerThread; j++) {
                        // 设置线程特有的AID
                        AdamTraceContext.setAid(threadAid);
                        
                        // 执行RPC调用
                        UserInfo userInfo = userService.getUserById("user-" + threadId + "-" + j);
                        
                        // 收集结果
                        threadResults.add(userInfo.getAid());
                    }
                    
                    aidResults.put(threadAid, threadResults);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    AdamTraceContext.clearAid();
                    endLatch.countDown();
                }
            });
        }
        
        // 开始测试
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue("所有线程应该在30秒内完成", endLatch.await(30, TimeUnit.SECONDS));
        
        // 验证结果
        assertEquals("应该有" + threadCount + "个线程的结果", threadCount, aidResults.size());
        
        for (Map.Entry<String, List<String>> entry : aidResults.entrySet()) {
            String expectedAid = entry.getKey();
            List<String> actualAids = entry.getValue();
            
            assertEquals("每个线程应该有" + callsPerThread + "次调用结果", callsPerThread, actualAids.size());
            
            for (String actualAid : actualAids) {
                assertEquals("AID应该保持线程隔离", expectedAid, actualAid);
            }
        }
        
        System.out.println("并发测试完成，验证了AID的线程隔离性");
    }

    /**
     * 测试RPC调用链中的AID传播
     * Consumer -> Provider -> 内部服务调用
     */
    @Test
    public void testRpcCallChainPropagation() {
        String originalAid = "rpc-chain-" + UUID.randomUUID();
        
        // 设置初始AID
        AdamTraceContext.setAid(originalAid);
        
        // 第一次RPC调用
        UserInfo userInfo = userService.getUserById("chain-user");
        assertEquals("第一次调用AID应该正确", originalAid, userInfo.getAid());
        
        // 继续使用相同的AID进行第二次调用
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.setUserId("chain-user");
        orderRequest.setTotalAmount(new BigDecimal("299.99"));
        orderRequest.setDeliveryAddress("调用链测试地址");
        
        OrderRequest.OrderItem item = new OrderRequest.OrderItem();
        item.setProductId("chain-prod");
        item.setProductName("调用链测试商品");
        item.setQuantity(1);
        item.setPrice(new BigDecimal("299.99"));
        orderRequest.setItems(Arrays.asList(item));
        
        OrderResponse orderResponse = userService.createOrder(orderRequest);
        assertEquals("第二次调用AID应该保持一致", originalAid, orderResponse.getAid());
        
        // 第三次调用通知服务
        boolean notificationResult = userService.sendNotification("chain-user", "调用链测试通知");
        assertTrue("通知发送应该成功", notificationResult);
        
        System.out.println("RPC调用链测试完成，AID在整个调用链中保持一致");
    }

    /**
     * 测试异常情况下的AID传播
     */
    @Test
    public void testExceptionHandlingWithAidPropagation() {
        String originalAid = "exception-test-" + UUID.randomUUID();
        
        // 设置AID
        AdamTraceContext.setAid(originalAid);
        
        // 创建无效的订单请求
        OrderRequest invalidRequest = new OrderRequest();
        // 故意不设置必需的字段，触发业务异常
        
        OrderResponse response = userService.createOrder(invalidRequest);
        
        // 验证异常响应中的AID传播
        assertNotNull("响应不应该为null", response);
        assertFalse("订单创建应该失败", response.isSuccess());
        assertEquals("AID应该在异常情况下也能正确传播", originalAid, response.getAid());
        assertNotNull("TraceId应该存在", response.getTraceId());
        
        System.out.println("异常处理测试完成: " + response);
    }
} 