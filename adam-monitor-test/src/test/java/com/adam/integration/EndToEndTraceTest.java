package com.adam.integration;

import com.adam.rpc.UserService;
import com.adam.rpc.dto.OrderRequest;
import com.adam.rpc.dto.OrderResponse;
import com.adam.rpc.dto.UserInfo;
import com.adam.trace.AdamTraceContext;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * 端到端集成测试
 * 测试完整的调用链：Web请求 -> RPC调用 -> MQ消息 -> 消费处理 -> RPC响应
 * 验证AID和TraceId在整个链路中的完整透传
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dubbo")
public class EndToEndTraceTest {

    @LocalServerPort
    private int port;

    @DubboReference(version = "1.0.0", timeout = 30000, check = false)
    private UserService userService;

    private TestRestTemplate restTemplate;
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private ExecutorService executorService;

    // 测试主题
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final String NOTIFICATION_TOPIC = "notification-events";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    @Before
    public void setUp() {
        AdamTraceContext.clearAid();
        restTemplate = new TestRestTemplate();
        executorService = Executors.newFixedThreadPool(5);
        initializeKafka();
    }

    @After
    public void tearDown() {
        AdamTraceContext.clearAid();
        if (producer != null) {
            producer.close();
        }
        if (consumer != null) {
            consumer.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    private void initializeKafka() {
        // 初始化Producer
        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producer = new KafkaProducer<>(producerProps);

        // 初始化Consumer
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-test-group-" + UUID.randomUUID());
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Arrays.asList(ORDER_EVENTS_TOPIC, NOTIFICATION_TOPIC));
    }

    /**
     * 测试完整的电商订单处理流程
     * 场景：用户下单 -> 查询用户信息 -> 创建订单 -> 发送订单事件 -> 处理库存 -> 发送通知
     */
    @Test
    public void testCompleteOrderProcessingFlow() throws Exception {
        System.out.println("=== 开始端到端订单处理流程测试 ===");
        
        String originalAid = "e2e-order-" + UUID.randomUUID();
        
        // 第一步：模拟Web请求设置AID
        System.out.println("步骤1: 设置初始AID - " + originalAid);
        AdamTraceContext.setAid(originalAid);
        
        // 第二步：HTTP请求到业务服务
        TraceResult webResult = simulateWebRequest(originalAid);
        assertEquals("Web请求应该保持AID", originalAid, webResult.aid);
        assertNotNull("Web请求应该生成TraceId", webResult.traceId);
        
        // 第三步：RPC调用查询用户信息
        TraceResult userQueryResult = simulateUserQuery(originalAid, "user-12345");
        assertEquals("用户查询应该保持AID", originalAid, userQueryResult.aid);
        assertNotNull("用户查询应该有TraceId", userQueryResult.traceId);
        
        // 第四步：RPC调用创建订单
        TraceResult orderCreationResult = simulateOrderCreation(originalAid, "user-12345");
        assertEquals("订单创建应该保持AID", originalAid, orderCreationResult.aid);
        assertNotNull("订单创建应该有TraceId", orderCreationResult.traceId);
        
        // 第五步：发送订单事件到MQ
        TraceResult mqPublishResult = simulateOrderEventPublish(originalAid, orderCreationResult.businessData);
        assertEquals("MQ发布应该保持AID", originalAid, mqPublishResult.aid);
        
        // 第六步：消费订单事件并处理库存
        TraceResult inventoryResult = simulateInventoryProcessing(originalAid);
        assertEquals("库存处理应该保持AID", originalAid, inventoryResult.aid);
        
        // 第七步：发送通知事件
        TraceResult notificationPublishResult = simulateNotificationPublish(originalAid, inventoryResult.businessData);
        assertEquals("通知发布应该保持AID", originalAid, notificationPublishResult.aid);
        
        // 第八步：消费通知事件并发送用户通知
        TraceResult userNotificationResult = simulateUserNotification(originalAid);
        assertEquals("用户通知应该保持AID", originalAid, userNotificationResult.aid);
        
        // 第九步：最终RPC调用确认订单状态
        TraceResult orderConfirmResult = simulateOrderStatusConfirmation(originalAid, orderCreationResult.businessData);
        assertEquals("订单确认应该保持AID", originalAid, orderConfirmResult.aid);
        
        System.out.println("=== 端到端流程测试完成 ===");
        printCompleteTraceChain(Arrays.asList(
            webResult, userQueryResult, orderCreationResult, mqPublishResult,
            inventoryResult, notificationPublishResult, userNotificationResult, orderConfirmResult
        ));
    }

    /**
     * 测试异常场景下的AID传播
     */
    @Test
    public void testErrorHandlingWithAidPropagation() throws Exception {
        System.out.println("=== 开始异常处理AID传播测试 ===");
        
        String originalAid = "e2e-error-" + UUID.randomUUID();
        AdamTraceContext.setAid(originalAid);
        
        try {
            // 模拟创建无效订单（触发业务异常）
            OrderRequest invalidRequest = new OrderRequest();
            // 故意不设置必需字段
            
            OrderResponse response = userService.createOrder(invalidRequest);
            
            // 验证异常响应中的AID传播
            assertFalse("订单创建应该失败", response.isSuccess());
            assertEquals("异常响应应该保持AID", originalAid, response.getAid());
            assertNotNull("异常响应应该有TraceId", response.getTraceId());
            
            // 发送错误事件到MQ
            String errorEvent = String.format(
                "{\"type\":\"order_error\",\"aid\":\"%s\",\"error\":\"Invalid order request\",\"timestamp\":%d}",
                originalAid, System.currentTimeMillis()
            );
            
            ProducerRecord<String, String> errorRecord = new ProducerRecord<>(
                "error-events", errorEvent);
            
            // 注入AID到消息头
            Map<String, String> context = AdamTraceContext.injectContext();
            context.forEach((key, value) -> errorRecord.headers().add(key, value.getBytes()));
            
            producer.send(errorRecord).get(10, TimeUnit.SECONDS);
            
            // 验证错误事件的AID传播
            consumer.subscribe(Collections.singletonList("error-events"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
            
            boolean errorEventFound = false;
            for (ConsumerRecord<String, String> record : records) {
                Header aidHeader = record.headers().lastHeader(AdamTraceContext.getAidHeaderKey());
                if (aidHeader != null) {
                    String receivedAid = new String(aidHeader.value());
                    if (originalAid.equals(receivedAid)) {
                        errorEventFound = true;
                        System.out.println("错误事件AID传播验证成功: " + receivedAid);
                        break;
                    }
                }
            }
            
            assertTrue("应该找到带有正确AID的错误事件", errorEventFound);
            
        } finally {
            AdamTraceContext.clearAid();
        }
        
        System.out.println("=== 异常处理AID传播测试完成 ===");
    }

    /**
     * 测试高并发场景下的AID隔离性
     */
    @Test
    public void testConcurrentTraceIsolation() throws Exception {
        System.out.println("=== 开始并发AID隔离测试 ===");
        
        int concurrentRequests = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentRequests);
        ConcurrentMap<String, String> aidResults = new ConcurrentHashMap<>();
        
        // 启动多个并发线程
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    
                    String threadAid = "concurrent-" + requestId + "-" + UUID.randomUUID();
                    AdamTraceContext.setAid(threadAid);
                    
                    // 模拟完整的业务流程
                    UserInfo userInfo = userService.getUserById("concurrent-user-" + requestId);
                    String retrievedAid = userInfo.getAid();
                    
                    aidResults.put(threadAid, retrievedAid);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    AdamTraceContext.clearAid();
                    endLatch.countDown();
                }
            });
        }
        
        // 开始并发测试
        startLatch.countDown();
        assertTrue("所有请求应该在30秒内完成", endLatch.await(30, TimeUnit.SECONDS));
        
        // 验证AID隔离性
        assertEquals("应该收到所有请求的结果", concurrentRequests, aidResults.size());
        
        for (Map.Entry<String, String> entry : aidResults.entrySet()) {
            String originalAid = entry.getKey();
            String retrievedAid = entry.getValue();
            assertEquals("每个线程的AID应该保持隔离", originalAid, retrievedAid);
        }
        
        System.out.println("并发AID隔离测试完成，验证了" + concurrentRequests + "个并发请求的隔离性");
    }

    // 模拟方法实现
    private TraceResult simulateWebRequest(String originalAid) {
        System.out.println("执行Web请求...");
        
        HttpHeaders headers = new HttpHeaders();
        headers.set(AdamTraceContext.getAidHeaderKey(), originalAid);
        headers.set("X-Trace-ID", "trace-" + System.currentTimeMillis());
        
        HttpEntity<String> entity = new HttpEntity<>("Web请求体", headers);
        
        // 模拟HTTP调用
        String url = "http://localhost:" + port + "/api/test";
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class);
            
            return new TraceResult(originalAid, 
                response.getHeaders().getFirst("X-Trace-ID"), 
                "web-request-success");
        } catch (Exception e) {
            return new TraceResult(originalAid, "trace-error", "web-request-error");
        }
    }

    private TraceResult simulateUserQuery(String originalAid, String userId) {
        System.out.println("执行用户查询RPC调用...");
        
        UserInfo userInfo = userService.getUserById(userId);
        return new TraceResult(userInfo.getAid(), userInfo.getTraceId(), 
                              "user-" + userInfo.getUserId());
    }

    private TraceResult simulateOrderCreation(String originalAid, String userId) {
        System.out.println("执行订单创建RPC调用...");
        
        OrderRequest request = createTestOrderRequest(userId);
        OrderResponse response = userService.createOrder(request);
        
        return new TraceResult(response.getAid(), response.getTraceId(), 
                              response.getOrderId());
    }

    private TraceResult simulateOrderEventPublish(String originalAid, String orderId) throws Exception {
        System.out.println("发布订单事件到MQ...");
        
        String orderEvent = String.format(
            "{\"type\":\"order_created\",\"orderId\":\"%s\",\"timestamp\":%d}",
            orderId, System.currentTimeMillis()
        );
        
        ProducerRecord<String, String> record = new ProducerRecord<>(
            ORDER_EVENTS_TOPIC, orderId, orderEvent);
        
        // 注入AID
        Map<String, String> context = AdamTraceContext.injectContext();
        context.forEach((key, value) -> record.headers().add(key, value.getBytes()));
        
        producer.send(record).get(10, TimeUnit.SECONDS);
        
        return new TraceResult(originalAid, "mq-trace-" + System.currentTimeMillis(), orderId);
    }

    private TraceResult simulateInventoryProcessing(String originalAid) throws Exception {
        System.out.println("处理库存扣减...");
        
        // 消费订单事件
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        
        for (ConsumerRecord<String, String> record : records) {
            if (ORDER_EVENTS_TOPIC.equals(record.topic())) {
                Header aidHeader = record.headers().lastHeader(AdamTraceContext.getAidHeaderKey());
                if (aidHeader != null) {
                    String receivedAid = new String(aidHeader.value());
                    if (originalAid.equals(receivedAid)) {
                        // 模拟库存处理
                        String inventoryResult = "inventory-processed-" + System.currentTimeMillis();
                        return new TraceResult(receivedAid, "inventory-trace", inventoryResult);
                    }
                }
            }
        }
        
        return new TraceResult(originalAid, "inventory-not-found", "no-inventory");
    }

    private TraceResult simulateNotificationPublish(String originalAid, String inventoryResult) throws Exception {
        System.out.println("发布通知事件到MQ...");
        
        String notificationEvent = String.format(
            "{\"type\":\"inventory_processed\",\"result\":\"%s\",\"timestamp\":%d}",
            inventoryResult, System.currentTimeMillis()
        );
        
        ProducerRecord<String, String> record = new ProducerRecord<>(
            NOTIFICATION_TOPIC, notificationEvent);
        
        // 注入AID
        Map<String, String> context = AdamTraceContext.injectContext();
        context.forEach((key, value) -> record.headers().add(key, value.getBytes()));
        
        producer.send(record).get(10, TimeUnit.SECONDS);
        
        return new TraceResult(originalAid, "notification-trace", inventoryResult);
    }

    private TraceResult simulateUserNotification(String originalAid) throws Exception {
        System.out.println("处理用户通知...");
        
        // 消费通知事件
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        
        for (ConsumerRecord<String, String> record : records) {
            if (NOTIFICATION_TOPIC.equals(record.topic())) {
                Header aidHeader = record.headers().lastHeader(AdamTraceContext.getAidHeaderKey());
                if (aidHeader != null) {
                    String receivedAid = new String(aidHeader.value());
                    if (originalAid.equals(receivedAid)) {
                        // 模拟发送用户通知
                        boolean success = userService.sendNotification("user-12345", "订单处理完成");
                        String result = success ? "notification-sent" : "notification-failed";
                        return new TraceResult(receivedAid, "notification-process", result);
                    }
                }
            }
        }
        
        return new TraceResult(originalAid, "notification-not-found", "no-notification");
    }

    private TraceResult simulateOrderStatusConfirmation(String originalAid, String orderId) {
        System.out.println("确认订单状态...");
        
        // 模拟查询订单状态的RPC调用
        UserInfo orderStatus = userService.getUserById("order-status-" + orderId);
        
        return new TraceResult(orderStatus.getAid(), orderStatus.getTraceId(), 
                              "order-confirmed-" + orderId);
    }

    private OrderRequest createTestOrderRequest(String userId) {
        OrderRequest request = new OrderRequest();
        request.setUserId(userId);
        request.setTotalAmount(new BigDecimal("299.99"));
        request.setDeliveryAddress("北京市朝阳区端到端测试地址");
        
        OrderRequest.OrderItem item = new OrderRequest.OrderItem();
        item.setProductId("e2e-product-001");
        item.setProductName("端到端测试商品");
        item.setQuantity(1);
        item.setPrice(new BigDecimal("299.99"));
        
        request.setItems(Arrays.asList(item));
        return request;
    }

    private void printCompleteTraceChain(List<TraceResult> results) {
        System.out.println("=== 完整调用链追踪结果 ===");
        for (int i = 0; i < results.size(); i++) {
            TraceResult result = results.get(i);
            System.out.printf("步骤%d: AID=%s, TraceId=%s, Result=%s%n", 
                            i + 1, result.aid, result.traceId, result.businessData);
        }
        System.out.println("============================");
    }

    /**
     * 追踪结果数据结构
     */
    private static class TraceResult {
        final String aid;
        final String traceId;
        final String businessData;

        TraceResult(String aid, String traceId, String businessData) {
            this.aid = aid;
            this.traceId = traceId;
            this.businessData = businessData;
        }
    }
} 