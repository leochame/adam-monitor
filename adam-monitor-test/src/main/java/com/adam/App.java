package com.adam;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class App {

    public static void main(String[] args) {
        // 创建消费者配置
        Properties consumerConfig = new Properties();
        consumerConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092"); // Kafka 服务端地址
        consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "my-consumer-group"); // 消费者组
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); // 消息 Key 的反序列化
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); // 消息 Value 的反序列化

        // 创建 Kafka 消费者
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerConfig);

        // 订阅 topic
        consumer.subscribe(Collections.singletonList("logs-topic4"));

        // 消费循环
        try {
            while (true) {
                // 拉取消息
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100)); // 每100ms 拉取一次

                // 处理拉取到的消息
                for (ConsumerRecord<String, String> record : records) {
                    // 在这里丢弃消息，实际上什么也不做
                    System.out.println("Received message with key: " + record.key() + " and value: " + record.value());
                }

                // 手动提交偏移量（如果你不使用自动提交）
                consumer.commitAsync((offsets, exception) -> {
                    if (exception != null) {
                        System.err.println("Error committing offsets: " + exception.getMessage());
                    }
                });
            }
        } catch (WakeupException e) {
            // 这个异常是用于关闭消费者时正常退出的
            System.out.println("Shutdown initiated");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 确保消费者关闭
            try {
                consumer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
