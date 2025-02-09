package com.adam;

import org.apache.kafka.clients.producer.*;
import java.util.Properties;

public class KafkaProducerTest {
    private static final String TOPIC = "test-topic";
    private static final String BOOTSTRAP_SERVERS = "127.0.0.1:9092";

    public static void main(String[] args) {
        // 配置生产者属性
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");

        // 创建生产者实例
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            // 创建消息记录
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, "test-key", "Hello, Kafka!");

            // 发送消息并处理回调
            producer.send(record, new Callback() {
                @Override
                public void onCompletion(RecordMetadata metadata, Exception exception) {
                    if (exception != null) {
                        System.err.println("消息发送失败: " + exception.getMessage());
                    } else {
                        System.out.println("消息发送成功，分区: " + metadata.partition() + ", 偏移量: " + metadata.offset());
                    }
                }
            });

            // 确保所有消息都被发送
            producer.flush();
        } catch (Exception e) {
            System.err.println("生产者发生错误: " + e.getMessage());
        }
    }
}