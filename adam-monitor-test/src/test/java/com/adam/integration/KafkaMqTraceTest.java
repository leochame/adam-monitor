package com.adam.integration;

import com.adam.trace.AdamTraceContext;
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
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Kafka MQ集成测试 - 测试消息头中AID和TraceId的透传
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class KafkaMqTraceTest {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String TEST_TOPIC = "adam-trace-test-topic";
    
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private ExecutorService executorService;

    @Before
    public void setUp() {
        AdamTraceContext.clearAid();
        executorService = Executors.newFixedThreadPool(5);
        initializeKafkaProducer();
        initializeKafkaConsumer();
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

    private void initializeKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        
        producer = new KafkaProducer<>(props);
    }

    private void initializeKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "adam-trace-test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TEST_TOPIC));
    }

    /**
     * 测试基本的Kafka消息中AID透传
     */
    @Test
    public void testBasicMessageWithAidPropagation() throws Exception {
        String originalAid = "kafka-basic-" + UUID.randomUUID();
        String messageContent = "基本测试消息";
        
        // 生产者设置AID并发送消息
        AdamTraceContext.setAid(originalAid);
        
        ProducerRecord<String, String> record = new ProducerRecord<>(TEST_TOPIC, messageContent);
        
        // 注入AID到消息头
        Map<String, String> context = AdamTraceContext.injectContext();
        context.forEach((key, value) -> record.headers().add(key, value.getBytes()));
        
        // 添加自定义TraceId到消息头
        String customTraceId = "trace-" + System.currentTimeMillis();
        record.headers().add("X-Trace-ID", customTraceId.getBytes());
        
        // 发送消息
        Future<RecordMetadata> sendResult = producer.send(record);
        RecordMetadata metadata = sendResult.get(10, TimeUnit.SECONDS);
        
        assertNotNull("消息发送元数据不应该为null", metadata);
        
        // 清除生产者上下文
        AdamTraceContext.clearAid();
        
        // 消费者接收消息
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
        assertFalse("应该收到消息", records.isEmpty());
        
        for (ConsumerRecord<String, String> consumedRecord : records) {
            assertEquals("消息内容应该正确", messageContent, consumedRecord.value());
            
            // 提取消息头中的AID
            Header aidHeader = consumedRecord.headers().lastHeader(AdamTraceContext.getAidHeaderKey());
            assertNotNull("消息头中应该包含AID", aidHeader);
            
            String receivedAid = new String(aidHeader.value());
            assertEquals("接收到的AID应该与发送的一致", originalAid, receivedAid);
            
            // 提取TraceId
            Header traceIdHeader = consumedRecord.headers().lastHeader("X-Trace-ID");
            assertNotNull("消息头中应该包含TraceId", traceIdHeader);
            
            String receivedTraceId = new String(traceIdHeader.value());
            assertEquals("接收到的TraceId应该与发送的一致", customTraceId, receivedTraceId);
            
            System.out.println("基本消息测试完成 - AID: " + receivedAid + ", TraceId: " + receivedTraceId);
        }
        
        consumer.commitSync();
    }

    /**
     * 测试批量消息发送中的AID透传
     */
    @Test
    public void testBatchMessagesWithAidPropagation() throws Exception {
        String batchAid = "kafka-batch-" + UUID.randomUUID();
        int messageCount = 10;
        
        // 设置批次AID
        AdamTraceContext.setAid(batchAid);
        
        List<Future<RecordMetadata>> sendResults = new ArrayList<>();
        
        // 批量发送消息
        for (int i = 0; i < messageCount; i++) {
            String messageContent = "批量消息-" + i;
            ProducerRecord<String, String> record = new ProducerRecord<>(TEST_TOPIC, 
                String.valueOf(i), messageContent);
            
            // 注入AID到每条消息
            Map<String, String> context = AdamTraceContext.injectContext();
            context.forEach((key, value) -> record.headers().add(key, value.getBytes()));
            
            // 添加消息序号
            record.headers().add("X-Message-Index", String.valueOf(i).getBytes());
            
            Future<RecordMetadata> result = producer.send(record);
            sendResults.add(result);
        }
        
        // 等待所有消息发送完成
        for (Future<RecordMetadata> result : sendResults) {
            result.get(10, TimeUnit.SECONDS);
        }
        
        // 清除生产者上下文
        AdamTraceContext.clearAid();
        
        // 消费批量消息
        Set<Integer> receivedIndexes = new HashSet<>();
        long startTime = System.currentTimeMillis();
        
        while (receivedIndexes.size() < messageCount && 
               System.currentTimeMillis() - startTime < 30000) {
            
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            
            for (ConsumerRecord<String, String> record : records) {
                // 验证AID
                Header aidHeader = record.headers().lastHeader(AdamTraceContext.getAidHeaderKey());
                assertNotNull("每条消息都应该包含AID", aidHeader);
                
                String receivedAid = new String(aidHeader.value());
                assertEquals("所有消息的AID应该一致", batchAid, receivedAid);
                
                // 验证消息序号
                Header indexHeader = record.headers().lastHeader("X-Message-Index");
                assertNotNull("每条消息都应该包含序号", indexHeader);
                
                int messageIndex = Integer.parseInt(new String(indexHeader.value()));
                receivedIndexes.add(messageIndex);
                
                System.out.println("收到批量消息 - 序号: " + messageIndex + ", AID: " + receivedAid);
            }
        }
        
        assertEquals("应该收到所有批量消息", messageCount, receivedIndexes.size());
        consumer.commitSync();
    }

    /**
     * 测试多生产者并发发送中的AID隔离
     */
    @Test
    public void testConcurrentProducersWithAidIsolation() throws Exception {
        int producerCount = 5;
        int messagesPerProducer = 3;
        
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(producerCount);
        ConcurrentMap<String, List<String>> producerResults = new ConcurrentHashMap<>();
        
        // 启动多个生产者线程
        for (int i = 0; i < producerCount; i++) {
            final int producerId = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    
                    String producerAid = "producer-" + producerId + "-" + UUID.randomUUID();
                    List<String> sentMessages = new ArrayList<>();
                    
                    // 创建独立的生产者
                    Properties props = new Properties();
                    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
                    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
                    props.put(ProducerConfig.ACKS_CONFIG, "all");
                    
                    try (KafkaProducer<String, String> threadProducer = new KafkaProducer<>(props)) {
                        // 设置线程特有的AID
                        AdamTraceContext.setAid(producerAid);
                        
                        for (int j = 0; j < messagesPerProducer; j++) {
                            String messageContent = "生产者-" + producerId + "-消息-" + j;
                            ProducerRecord<String, String> record = new ProducerRecord<>(
                                TEST_TOPIC, "producer-" + producerId, messageContent);
                            
                            // 注入AID
                            Map<String, String> context = AdamTraceContext.injectContext();
                            context.forEach((key, value) -> record.headers().add(key, value.getBytes()));
                            
                            // 添加生产者ID和消息ID
                            record.headers().add("X-Producer-ID", String.valueOf(producerId).getBytes());
                            record.headers().add("X-Message-ID", String.valueOf(j).getBytes());
                            
                            threadProducer.send(record).get(5, TimeUnit.SECONDS);
                            sentMessages.add(messageContent);
                        }
                    }
                    
                    producerResults.put(producerAid, sentMessages);
                    
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
        
        // 等待所有生产者完成
        assertTrue("所有生产者应该在30秒内完成", endLatch.await(30, TimeUnit.SECONDS));
        
        // 验证生产者结果
        assertEquals("应该有" + producerCount + "个生产者的结果", producerCount, producerResults.size());
        
        // 消费所有消息并验证AID隔离
        Map<String, Set<String>> aidToMessages = new HashMap<>();
        long startTime = System.currentTimeMillis();
        int expectedTotalMessages = producerCount * messagesPerProducer;
        int receivedCount = 0;
        
        while (receivedCount < expectedTotalMessages && 
               System.currentTimeMillis() - startTime < 30000) {
            
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            
            for (ConsumerRecord<String, String> record : records) {
                // 提取AID
                Header aidHeader = record.headers().lastHeader(AdamTraceContext.getAidHeaderKey());
                assertNotNull("每条消息都应该包含AID", aidHeader);
                
                String receivedAid = new String(aidHeader.value());
                String messageContent = record.value();
                
                aidToMessages.computeIfAbsent(receivedAid, k -> new HashSet<>()).add(messageContent);
                receivedCount++;
            }
        }
        
        // 验证AID隔离性
        assertEquals("应该收到所有消息", expectedTotalMessages, receivedCount);
        assertEquals("AID数量应该等于生产者数量", producerCount, aidToMessages.size());
        
        for (Map.Entry<String, Set<String>> entry : aidToMessages.entrySet()) {
            assertEquals("每个AID应该对应" + messagesPerProducer + "条消息", 
                        messagesPerProducer, entry.getValue().size());
        }
        
        consumer.commitSync();
        System.out.println("并发生产者测试完成，验证了AID的隔离性");
    }

    /**
     * 测试消息重试场景下的AID保持
     */
    @Test
    public void testMessageRetryWithAidConsistency() throws Exception {
        String retryAid = "kafka-retry-" + UUID.randomUUID();
        String messageContent = "重试测试消息";
        
        // 设置AID
        AdamTraceContext.setAid(retryAid);
        
        // 模拟重试场景：发送多次相同的消息（带有不同的重试标记）
        for (int retryCount = 0; retryCount < 3; retryCount++) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                TEST_TOPIC, messageContent + "-重试-" + retryCount);
            
            // 注入AID（应该在所有重试中保持一致）
            Map<String, String> context = AdamTraceContext.injectContext();
            context.forEach((key, value) -> record.headers().add(key, value.getBytes()));
            
            // 添加重试标记
            record.headers().add("X-Retry-Count", String.valueOf(retryCount).getBytes());
            record.headers().add("X-Original-Message", messageContent.getBytes());
            
            producer.send(record).get(10, TimeUnit.SECONDS);
            
            // 模拟重试间隔
            Thread.sleep(100);
        }
        
        // 清除生产者上下文
        AdamTraceContext.clearAid();
        
        // 消费重试消息
        List<Integer> retryNumbers = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        while (retryNumbers.size() < 3 && System.currentTimeMillis() - startTime < 15000) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            
            for (ConsumerRecord<String, String> record : records) {
                // 验证AID在所有重试中保持一致
                Header aidHeader = record.headers().lastHeader(AdamTraceContext.getAidHeaderKey());
                assertNotNull("重试消息应该包含AID", aidHeader);
                
                String receivedAid = new String(aidHeader.value());
                assertEquals("重试消息的AID应该保持一致", retryAid, receivedAid);
                
                // 获取重试次数
                Header retryHeader = record.headers().lastHeader("X-Retry-Count");
                assertNotNull("重试消息应该包含重试次数", retryHeader);
                
                int retryCount = Integer.parseInt(new String(retryHeader.value()));
                retryNumbers.add(retryCount);
                
                System.out.println("收到重试消息 - 重试次数: " + retryCount + ", AID: " + receivedAid);
            }
        }
        
        assertEquals("应该收到3条重试消息", 3, retryNumbers.size());
        Collections.sort(retryNumbers);
        assertEquals("重试消息应该包含正确的重试次数", Arrays.asList(0, 1, 2), retryNumbers);
        
        consumer.commitSync();
    }

    /**
     * 测试消息顺序性和AID透传
     */
    @Test
    public void testOrderedMessagesWithAidPropagation() throws Exception {
        String orderAid = "kafka-order-" + UUID.randomUUID();
        int messageCount = 5;
        int partition = 0; // 使用固定分区确保顺序
        
        // 设置AID
        AdamTraceContext.setAid(orderAid);
        
        // 按顺序发送消息
        for (int i = 0; i < messageCount; i++) {
            String messageContent = "有序消息-" + i;
            ProducerRecord<String, String> record = new ProducerRecord<>(
                TEST_TOPIC, partition, String.valueOf(i), messageContent);
            
            // 注入AID
            Map<String, String> context = AdamTraceContext.injectContext();
            context.forEach((key, value) -> record.headers().add(key, value.getBytes()));
            
            // 添加序号
            record.headers().add("X-Sequence", String.valueOf(i).getBytes());
            
            // 同步发送确保顺序
            producer.send(record).get(10, TimeUnit.SECONDS);
        }
        
        // 清除生产者上下文
        AdamTraceContext.clearAid();
        
        // 按顺序消费消息
        List<Integer> receivedSequences = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        
        while (receivedSequences.size() < messageCount && 
               System.currentTimeMillis() - startTime < 15000) {
            
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
            
            for (ConsumerRecord<String, String> record : records) {
                // 验证分区
                assertEquals("消息应该在指定分区", partition, record.partition());
                
                // 验证AID
                Header aidHeader = record.headers().lastHeader(AdamTraceContext.getAidHeaderKey());
                assertNotNull("有序消息应该包含AID", aidHeader);
                
                String receivedAid = new String(aidHeader.value());
                assertEquals("有序消息的AID应该正确", orderAid, receivedAid);
                
                // 获取序号
                Header sequenceHeader = record.headers().lastHeader("X-Sequence");
                assertNotNull("有序消息应该包含序号", sequenceHeader);
                
                int sequence = Integer.parseInt(new String(sequenceHeader.value()));
                receivedSequences.add(sequence);
                
                System.out.println("收到有序消息 - 序号: " + sequence + ", AID: " + receivedAid);
            }
        }
        
        // 验证消息顺序
        assertEquals("应该收到所有有序消息", messageCount, receivedSequences.size());
        
        for (int i = 0; i < messageCount; i++) {
            assertEquals("消息顺序应该正确", Integer.valueOf(i), receivedSequences.get(i));
        }
        
        consumer.commitSync();
        System.out.println("有序消息测试完成，验证了消息顺序和AID透传");
    }
} 