package com.adam.listener;

import com.adam.service.LogAnalyticalService;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Flink日志流处理器
 * 替代原有的BatchLogStreamHandler，使用Flink处理Kafka中的日志消息
 */
@Component
@Slf4j
public class LogStreamProcessor {

    @Autowired
    private StreamExecutionEnvironment env;

    @Autowired
    private LogAnalyticalService logAnalyticalService;

    @Value("${kafka.hosts}")
    private String kafkaHosts;

    @Value("${kafka.group}")
    private String kafkaGroup;

    private static final String TOPIC_NAME = "logs-topic4";
    private static final int WINDOW_SIZE_SECONDS = 3;
    private static final int BATCH_SIZE = 100;

    private final AtomicInteger processedBatchCount = new AtomicInteger(0);
    
    @PostConstruct
    public void init() throws Exception {
        // 配置Kafka数据源
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaHosts)
                .setTopics(TOPIC_NAME)
                .setGroupId(kafkaGroup)
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        // 创建数据流
        DataStream<String> stream = env.fromSource(
                source,
                WatermarkStrategy.noWatermarks(),
                "Kafka Source"
        );

        // 处理日志消息
        stream
                .map(new MapFunction<String, String>() {
                    @Override
                    public String map(String value) {
                        // 可以在这里进行一些预处理，例如验证JSON格式等
                        return value;
                    }
                })
                .keyBy(value -> "default") // 使用固定键，类似于原来的实现
                .window(TumblingProcessingTimeWindows.of(Time.seconds(WINDOW_SIZE_SECONDS)))
                .process(new ProcessWindowFunction<String, List<String>, String, TimeWindow>() {
                    @Override
                    public void process(String key, 
                                      ProcessWindowFunction<String, List<String>, String, TimeWindow>.Context context,
                                      Iterable<String> elements, 
                                      Collector<List<String>> out) {
                        List<String> batch = new ArrayList<>();
                        for (String element : elements) {
                            batch.add(element);
                            
                            // 当批次达到指定大小时，进行处理
                            if (batch.size() >= BATCH_SIZE) {
                                out.collect(new ArrayList<>(batch));
                                batch.clear();
                            }
                        }
                        
                        // 处理剩余的元素
                        if (!batch.isEmpty()) {
                            out.collect(batch);
                        }
                    }
                })
                .addSink(new SinkFunction<List<String>>() {
                    @Override
                    public void invoke(List<String> logBatch, Context context) {
                        try {
                            // 批量插入日志
                            logAnalyticalService.saveAll(logBatch);
                            int batchCount = processedBatchCount.incrementAndGet();
                            log.info("成功插入批次 #{}: {} 条日志", batchCount, logBatch.size());
                        } catch (Exception e) {
                            log.error("批量插入日志失败: {}", e.getMessage(), e);
                        }
                    }
                });

        // 以分离的线程启动Flink作业
        Thread flinkJobThread = new Thread(() -> {
            try {
                log.info("开始执行Flink日志处理作业...");
                env.execute("Log Stream Processing");
            } catch (Exception e) {
                log.error("Flink作业执行失败: {}", e.getMessage(), e);
            }
        });
        flinkJobThread.setDaemon(true); // 设置为守护线程，避免阻止JVM退出
        flinkJobThread.start();

        log.info("Flink日志处理作业已启动");
    }
}