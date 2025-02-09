package com.adam.listener;

import com.adam.service.LogAnalyticalService;
import com.adam.utils.ProtostuffUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

@Configuration
@Slf4j
public class BatchLogStreamHandler {

    @Autowired
    private LogAnalyticalService logAnalyticalService;

    private static final int BATCH_SIZE = 100;  // 批量大小，仍然可以保留此逻辑

    @Bean
   public KStream<String, byte[]> kStream(StreamsBuilder builder) {
        KStream<String, byte[]> stream = builder.stream("logs-topic",
                Consumed.with(Serdes.String(), Serdes.ByteArray()));

       stream
               // 解析日志并提取时间戳
               .map((key, value) -> {
                   LogMessage log = ProtostuffUtil.deserialize(value, LogMessage.class);
                   return new KeyValue<>(log.getSystemName(), log);
               })
               // 按系统名称分组，并定义时间窗口
               .groupByKey()
               .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(3)))
               // 聚合窗口内日志（可自定义聚合逻辑）
               .aggregate(
                       LinkedList<LogMessage>::new,
                       (key, log, agg) -> {
                           agg.add(log);
                           return agg;
                       },
                       Materialized.as("log-windowed-store")
               )
               .toStream()
               // 触发批量插入（窗口关闭时自动触发）
               .foreach((windowedKey, logs) -> {
                   if (!logs.isEmpty()) {
                       batchInsert(logs);
                       log.info("窗口 [{} - {}] 插入 {} 条日志",
                               windowedKey.window().startTime(),
                               windowedKey.window().endTime(),
                               logs.size()
                       );
                   }
               });

       return stream;
        }

    // 批量插入日志消息
    private void batchInsert(List<LogMessage> logBatch) {
        // 假设你用某种方式将数据插入到数据库
        logAnalyticalService.saveAll(logBatch);
        log.info("批量插入日志数据，插入了 {} 条数据", logBatch.size());
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

    // 时间戳提取器
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
