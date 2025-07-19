package com.adam.listener;

import com.adam.entitys.LogMessage;
import com.adam.service.LogAnalyticalService;
import com.alibaba.fastjson.JSON;
import org.apache.kafka.streams.processor.Processor;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Duration;
import org.apache.kafka.streams.processor.PunctuationType;

/**
 * Flink日志流处理器
 * 替代原有的BatchLogStreamHandler，使用Flink处理Kafka中的日志消息
 */
@Component
@Slf4j
public class LogStreamProcessor implements Processor<String, byte[]> {

    private static final Logger log = LoggerFactory.getLogger(LogStreamProcessor.class);

    private ProcessorContext context;
    private final List<LogMessage> buffer = new ArrayList<>();
    private final int batchSize;
    private final LogAnalyticalService analyticalService;

    public LogStreamProcessor(int batchSize, LogAnalyticalService analyticalService) {
        this.batchSize = batchSize;
        this.analyticalService = analyticalService;
    }

    @Override
    public void init(ProcessorContext context) {
        this.context = context;
        // 定时处理缓冲区
        this.context.schedule(Duration.ofSeconds(10), PunctuationType.WALL_CLOCK_TIME, (timestamp) -> processBuffer());
        log.info("LogStreamProcessor initialized.");
    }

    @Override
    public void process(String key, byte[] value) {
        // 将JSON字节数组转换为LogMessage对象
        String json = new String(value, StandardCharsets.UTF_8);
        try {
            LogMessage logMessage = JSON.parseObject(json, LogMessage.class);
            if (logMessage != null) {
                buffer.add(logMessage);
                log.debug("Added log to buffer. Buffer size: {}", buffer.size());
            } else {
                log.warn("Parsed LogMessage is null for JSON: {}", json);
            }
        } catch (Exception e) {
            log.error("Failed to parse JSON to LogMessage: " + json, e);
        }

        // 达到批量大小时处理
        if (buffer.size() >= batchSize) {
            processBuffer();
        }
    }

    private void processBuffer() {
        if (buffer.isEmpty()) {
            return;
        }

        log.info("Processing buffer with {} logs.", buffer.size());
        try {
            // 在这里实现批量处理逻辑，例如，将它们发送到另一个系统或数据库
            // analyticalService.analytical(new ArrayList<>(buffer));
            log.info("Successfully processed {} logs.", buffer.size());
        } catch (Exception e) {
            log.error("Error processing log buffer.", e);
            // 可以在这里添加重试逻辑或错误处理
        } finally {
            buffer.clear();
            log.debug("Buffer cleared.");
        }
    }

    @Override
    public void close() {
        // 在关闭前处理剩余的日志
        log.info("Closing processor, processing remaining logs in buffer.");
        processBuffer();
    }
}