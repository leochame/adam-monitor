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
import java.util.Arrays;
import java.util.List;

@Configuration
@Slf4j
public class BatchLogStreamHandler {

    @Autowired
    private LogAnalyticalService logAnalyticalService;

    private static final int BATCH_SIZE = 100;  // 批量大小，仍然可以保留此逻辑

    @Bean
    public KStream<String, String> kStream(StreamsBuilder builder) {
        KStream<String, String> stream = builder.stream("logs-topic4");

// 处理日志、窗口聚合，并触发批量插入
        KTable<Windowed<String>, String> aggregatedTable = stream
                .map((key, value) -> {
                    return new KeyValue<>("default", value);
                })
                .filter((key, value) -> value != null)
                .groupByKey()
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(3)))
                .aggregate(
                        () -> "",
                        (key, value, aggregate) -> {
                            if (aggregate.isEmpty()) {
                                return value;
                            } else {
                                return aggregate + "@#@" + value; // 使用逗号分隔
                            }

                        },
                        Materialized.with(Serdes.String(), Serdes.String())
                );

// 将聚合结果批量插入数据库
        aggregatedTable.toStream().foreach((windowedKey, aggregatedValue) -> {
            try {
                List<String> logList = Arrays.asList(aggregatedValue.split("@#@"));
                System.out.println(logList);
                batchInsert(logList);
                System.out.println("成功插入批次: " + logList.size() + " 条");
            } catch (Exception e) {
                System.err.println("插入失败: " + e.getMessage());
            }
        });
        return stream;
    }

    // 批量插入日志消息
    private void batchInsert(List<String> logBatch) {
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

}
