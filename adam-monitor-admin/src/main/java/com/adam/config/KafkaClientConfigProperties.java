package com.adam.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "spring.kafka")
public class KafkaClientConfigProperties {
    private String bootstrapServers;
    private String groupId;
    private String topic;

}
