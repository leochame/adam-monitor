package com.adam.config;


import com.adam.listener.LogMessage;
import com.adam.listener.MonitorLogListener;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
     * Redisson 客户端配置类
     */
    @Configuration
public class RedisClientConfig {

   private RedisClientConfigProperties properties;

   @Bean("redissonClient")
   public RedissonClient redissonClient() {
       Config config = new Config();
       config.setCodec(JsonJacksonCodec.INSTANCE);  // 使用 Jackson 编解码器

       // Redis 单节点配置
       config.useSingleServer()
               .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
               .setConnectionPoolSize(properties.getPoolSize())
               .setConnectionMinimumIdleSize(properties.getMinIdleSize())
               .setIdleConnectionTimeout(properties.getIdleTimeout())
               .setConnectTimeout(properties.getConnectTimeout())
               .setRetryAttempts(properties.getRetryAttempts())
               .setRetryInterval(properties.getRetryInterval())
               .setPingConnectionInterval(properties.getPingInterval())
               .setKeepAlive(properties.isKeepAlive());

       return Redisson.create(config);  // 创建 Redisson 客户端实例
   }
    /**
     * 业务行为监控主题配置
     * @param redissonClient 注入的 Redisson 客户端
     * @param monitorLogListener 日志监听处理器
     * @return 初始化后的消息主题
     */
    @Bean("adam-monitor-topic")
    public RTopic businessBehaviorMonitorTopic(RedissonClient redissonClient,
                                               MonitorLogListener monitorLogListener) {
        // 获取或创建 Redis 消息主题
        RTopic topic = redissonClient.getTopic("business-behavior-monitor-sdk-topic");

        // 注册消息监听器，传入 LogMessage 类型和对应的监听器
        topic.addListener(LogMessage.class, monitorLogListener);

        return topic;
    }
}