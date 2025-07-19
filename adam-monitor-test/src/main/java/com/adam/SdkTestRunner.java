package com.adam;

import com.adam.appender.CustomAppender;
import com.adam.trace.AdamTraceContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

/**
 * SDK功能测试运行器
 */
public class SdkTestRunner {

    public static void main(String[] args) {
        System.out.println("=== Adam Monitor SDK 功能测试 ===");
        
        // 测试1: AID和TraceId基本功能
        testAidAndTraceId();
        
        // 测试2: 直接Kafka投送
        testDirectKafkaDelivery();
        
        // 测试3: CustomAppender功能
        testCustomAppender();
        
        // 测试4: 带AID的Kafka消息投送
        testKafkaWithAid();
        
        System.out.println("=== 所有测试完成 ===");
    }
    
    /**
     * 测试AID和TraceId的基本设置和获取
     */
    private static void testAidAndTraceId() {
        System.out.println("\n--- 测试1: AID和TraceId基本功能 ---");
        
        String testAid = "test-aid-" + UUID.randomUUID();
        
        // 设置AID
        AdamTraceContext.setAid(testAid);
        
        // 验证设置成功
        String actualAid = AdamTraceContext.getAid();
        String actualTraceId = AdamTraceContext.getTraceId();
        
        System.out.println("设置AID: " + testAid);
        System.out.println("获取AID: " + actualAid);
        System.out.println("获取TraceId: " + actualTraceId);
        
        if (testAid.equals(actualAid)) {
            System.out.println("✅ AID设置和获取功能正常");
        } else {
            System.out.println("❌ AID设置和获取功能异常");
        }
        
        // 测试上下文注入
        java.util.Map<String, String> context = AdamTraceContext.injectContext();
        System.out.println("注入的上下文: " + context);
        
        // 清除AID
        AdamTraceContext.clearAid();
        System.out.println("清除后AID: " + AdamTraceContext.getAid());
    }
    
    /**
     * 测试直接Kafka投送
     */
    private static void testDirectKafkaDelivery() {
        System.out.println("\n--- 测试2: 直接Kafka投送 ---");
        
        try {
            // 创建Kafka生产者
            Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            
            KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);
            
            // 发送消息
            String message = "Direct Kafka test message - " + System.currentTimeMillis();
            String key = "test-key-" + UUID.randomUUID();
            ProducerRecord<String, String> record = new ProducerRecord<>("adam-monitor-logs", key, message);
            
            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    System.out.println("✅ 消息发送成功:");
                    System.out.println("  Topic: " + metadata.topic());
                    System.out.println("  Partition: " + metadata.partition());
                    System.out.println("  Offset: " + metadata.offset());
                } else {
                    System.out.println("❌ 消息发送失败: " + exception.getMessage());
                }
            });
            
            producer.flush();
            producer.close();
            
            // 等待一下，然后消费消息
            Thread.sleep(1000);
            
            // 创建Kafka消费者
            Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group");
            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
            consumer.subscribe(Arrays.asList("adam-monitor-logs"));
            
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
            for (ConsumerRecord<String, String> record2 : records) {
                System.out.println("✅ 接收到消息:");
                System.out.println("  Key: " + record2.key());
                System.out.println("  Value: " + record2.value());
                System.out.println("  Topic: " + record2.topic());
                System.out.println("  Partition: " + record2.partition());
                System.out.println("  Offset: " + record2.offset());
            }
            
            consumer.close();
            
        } catch (Exception e) {
            System.out.println("❌ Kafka测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试CustomAppender功能
     */
    private static void testCustomAppender() {
        System.out.println("\n--- 测试3: CustomAppender功能 ---");
        
        try {
            // 设置AID
            String testAid = "appender-test-aid-" + UUID.randomUUID();
            AdamTraceContext.setAid(testAid);
            
            // 创建CustomAppender
            CustomAppender<ILoggingEvent> appender = new CustomAppender<>();
            appender.setSystemName("test-system");
            appender.setGroupId("com.adam");
            appender.setHost("localhost");
            appender.setPort(9092);
            appender.setBatchSize(16384);
            appender.start();
            
            // 创建测试日志事件
            LoggingEvent event = new LoggingEvent();
            event.setMessage("Test log message from SDK");
            event.setTimeStamp(System.currentTimeMillis());
            event.setLevel(ch.qos.logback.classic.Level.INFO);
            event.setLoggerName("com.adam.test");
            
            // 发送日志
            appender.doAppend(event);
            System.out.println("✅ 日志事件已发送到CustomAppender");
            System.out.println("当前AID: " + AdamTraceContext.getAid());
            
            appender.stop();
            
        } catch (Exception e) {
            System.out.println("❌ CustomAppender测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试带AID的Kafka消息投送
     */
    private static void testKafkaWithAid() {
        System.out.println("\n--- 测试4: 带AID的Kafka消息投送 ---");
        
        try {
            // 设置AID
            String testAid = "kafka-aid-" + UUID.randomUUID();
            AdamTraceContext.setAid(testAid);
            
            // 创建Kafka生产者
            Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            
            KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps);
            
            // 发送带AID的消息
            String message = "Message with AID: " + testAid + " - " + System.currentTimeMillis();
            String key = "aid-key-" + UUID.randomUUID();
            ProducerRecord<String, String> record = new ProducerRecord<>("adam-monitor-logs", key, message);
            
            // 将AID添加到消息头
            record.headers().add("X-Adam-AID", testAid.getBytes());
            record.headers().add("X-Trace-ID", ("trace-" + System.currentTimeMillis()).getBytes());
            
            producer.send(record, (metadata, exception) -> {
                if (exception == null) {
                    System.out.println("✅ 带AID的消息发送成功:");
                    System.out.println("  Topic: " + metadata.topic());
                    System.out.println("  Partition: " + metadata.partition());
                    System.out.println("  Offset: " + metadata.offset());
                    System.out.println("  AID: " + testAid);
                } else {
                    System.out.println("❌ 带AID的消息发送失败: " + exception.getMessage());
                }
            });
            
            producer.flush();
            producer.close();
            
        } catch (Exception e) {
            System.out.println("❌ 带AID的Kafka测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 