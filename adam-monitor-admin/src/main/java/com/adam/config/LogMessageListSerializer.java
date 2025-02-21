package com.adam.config;

import com.adam.listener.LogMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serializer;

import java.util.List;

public class LogMessageListSerializer implements Serializer<List<LogMessage>> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, List<LogMessage> data) {
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize List<LogMessage>", e);
        }
    }
}