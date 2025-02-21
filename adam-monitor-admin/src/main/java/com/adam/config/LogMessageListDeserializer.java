package com.adam.config;

import com.adam.listener.LogMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.List;

public class LogMessageListDeserializer implements Deserializer<List<LogMessage>> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<LogMessage> deserialize(String topic, byte[] data) {
        try {
            return objectMapper.readValue(data, new TypeReference<List<LogMessage>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize List<LogMessage>", e);
        }
    }
}