package com.adam.service;

import com.adam.listener.LogMessage;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class BatchLogStreamHandler {

    private static final List<LogMessage> logBuffer = new ArrayList<>();  // 用于缓冲批量日志
    private static final int BATCH_SIZE = 100;  // 设置批量插入的大小

    @Bean
    public KStream<String, String> kStream(StreamsBuilder streamsBuilder) {
        KStream<String, String> stream = streamsBuilder.stream("logs-topic");  // 假设 Kafka Topic 为 "logs-topic"

        stream.map((key, value) -> {
            // 这里只是一个示例，假设日志是简单的 JSON 格式，这里你可以根据实际的日志结构进行解析
            LogMessage logMessage = JSON.parseObject(value, LogMessage.class);
            return new KeyValue<>(logMessage.getLogId(), logMessage.getLogContent());
        })
        .peek((key, value) -> {
            // 将解析后的日志添加到缓冲区
            logBuffer.add(new LogMessage(key, value));

            // 如果缓冲区达到了批量插入的大小，就进行批量插入
            if (logBuffer.size() >= BATCH_SIZE) {
                batchInsert(logBuffer);
                logBuffer.clear();  // 清空缓冲区
            }
        })
        .to("processed-logs-topic");  // 处理后的日志发送到另一个 Kafka Topic

        return stream;
    }

    // 格式化日志对象
    public String formatLog(String logId, String logContent) {
        // 这里可以对日志进行任何必要的格式化操作
        // 假设这里直接返回原始日志内容
        return logContent;
    }

    // 批量插入日志消息
    private void batchInsert(List<LogMessage> logBatch) {
        // 假设你用某种方式将数据插入到数据库
        logRepository.saveAll(logBatch);
        log.info("批量插入日志数据，插入了 {} 条数据", logBatch.size());
    }

}
