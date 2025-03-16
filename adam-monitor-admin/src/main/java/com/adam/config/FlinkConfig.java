package com.adam.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Flink配置类
 * 替代原有的KafkaStreamConfig，提供Flink流处理环境
 */
@Setter
@Getter
@org.springframework.context.annotation.Configuration
@ConfigurationProperties(prefix="kafka")
public class FlinkConfig {
    private String hosts;
    private String group;
    
    // Flink检查点目录
    private static final String CHECKPOINT_DIR = "file:///tmp/flink-checkpoints";
    
    @SuppressWarnings("deprecation")
    @Bean
    public StreamExecutionEnvironment streamExecutionEnvironment() {
        // 创建Flink流执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        
        // 配置检查点，确保数据处理的可靠性
        env.enableCheckpointing(60000); // 每60秒做一次检查点
        env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(30000); // 两次检查点之间的最小时间间隔
        env.getCheckpointConfig().setCheckpointTimeout(120000); // 检查点超时时间
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1); // 同时只允许一个检查点
        env.getCheckpointConfig().setExternalizedCheckpointCleanup(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION); // 取消作业时保留检查点
        
        // 配置状态后端，用于存储检查点
        env.setStateBackend(new FsStateBackend(CHECKPOINT_DIR));
        
        // 配置重启策略
        env.setRestartStrategy(RestartStrategies.fixedDelayRestart(
                3, // 最大重启次数
                Time.of(10, TimeUnit.SECONDS) // 重启间隔
        ));
        
        // 设置并行度
        env.setParallelism(1); // 根据实际需求调整
        
        return env;
    }
}