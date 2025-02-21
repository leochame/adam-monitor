package com.adam.config;

import com.adam.listener.LogMessage;
import org.apache.kafka.common.serialization.Deserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LogMessageDeserializer implements Deserializer<LogMessage> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public LogMessage deserialize(String topic, byte[] data) {
        try {
            return objectMapper.readValue(data, LogMessage.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize LogMessage", e);
        }
    }
}