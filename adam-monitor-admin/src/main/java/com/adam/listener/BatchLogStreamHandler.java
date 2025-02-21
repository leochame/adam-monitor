package com.adam.listener;

import com.adam.config.LogMessageListDeserializer;
import com.adam.config.LogMessageListSerializer;
import com.adam.service.LogAnalyticalService;
import com.adam.utils.ProtostuffUtil;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Configuration
@Slf4j
public class BatchLogStreamHandler {

    @Autowired
    private LogAnalyticalService logAnalyticalService;

    private static final int BATCH_SIZE = 100;  // 批量大小，仍然可以保留此逻辑

    @Bean
    public KStream<String, String> kStream(StreamsBuilder builder) {
        // 从Kafka主题 "logs-topic3" 创建KStream
        KStream<String, String> stream = builder.stream("logs-topic3");

        // 解析日志并提取时间戳
        KStream<String, LogMessage> parsedStream = stream.map((key, value) -> {
            // 将JSON字符串解析为LogMessage对象
            LogMessage log = JSON.parseObject(value, LogMessage.class);
            // 返回新的键值对，键为系统名称，值为日志对象
            return new KeyValue<>(log.getSystemName(), log);
        });

        // 过滤掉空日志
        KStream<String, LogMessage> filteredStream = parsedStream.filter((key, log) -> log != null);

        // 按系统名称分组，并定义时间窗口
        KGroupedStream<String, LogMessage> groupedStream = filteredStream.groupByKey();

        // 定义时间窗口（3秒窗口，无宽限期）
        TimeWindowedKStream<String, LogMessage> windowedStream = groupedStream
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(3)));

        // 聚合窗口内的日志
        // 聚合窗口内的日志
        KTable<Windowed<String>, List<LogMessage>> aggregatedTable = windowedStream
                .aggregate(
                        ArrayList::new, // 初始化聚合器
                        (key, log, agg) -> { // 聚合逻辑
                            agg.add(log);
                            return agg;
                        },
                        Materialized.<String, List<LogMessage>, WindowStore<Bytes, byte[]>>as("log-windowed-store")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.serdeFrom(new LogMessageListSerializer(), new LogMessageListDeserializer()))
                );


        // 将聚合结果转换为流
        KStream<Windowed<String>, List<LogMessage>> resultStream = aggregatedTable.toStream();

        // 处理每个窗口的日志
        resultStream.foreach((windowedKey, logs) -> {
            if (!logs.isEmpty()) {
                // 批量插入日志
                batchInsert(logs);
                // 记录日志插入信息
                log.info("窗口 [{} - {}] 插入 {} 条日志",
                        windowedKey.window().startTime(),
                        windowedKey.window().endTime(),
                        logs.size()
                );
            }
        });

        // 返回原始流
        return stream;
    }

    // 批量插入日志消息
    private void batchInsert(List<LogMessage> logBatch) {
        // 假设你用某种方式将数据插入到数据库
        logAnalyticalService.saveAll(logBatch);
        log.info("批量插入日志数据，插入了 {} 条数据", logBatch.size());
    }

    // 反序列化 JSON 数组为 List<LogMessage>
    private LogMessage deserializeJsonArray(byte[] value) {
        try {
            return ProtostuffUtil.deserialize(value,LogMessage.class);
        } catch (Exception e) {
            log.error("反序列化 JSON 数组失败", e);
            return null;
        }
    }


    // 高性能序列化
//    public static class LogMessageSerializer {
//        private static final ThreadLocal<ProtoBufEncoder> encoder =
//                ThreadLocal.withInitial(ProtoBufEncoder::new);
//
//        public static Serde<LogMessage> serde() {
//            return Serdes.serdeFrom(
//                    (topic, data) -> encoder.get().encode(data),
//                    (topic, bytes) -> encoder.get().decode(bytes)
//            );
//        }
//    }

//     时间戳提取器
//    public static class LogTimestampExtractor implements TimestampExtractor {
//        @Override
//        public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
//            try {
//                LogMessage log = LogMessageSerializer.deserialize((String) record.value());
//                return log.getTimestamp();
//            } catch (Exception e) {
//                return System.currentTimeMillis();
//            }
//        }
//    }
}
