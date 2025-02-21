package com.adam.config;

import com.adam.listener.LogMessage;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.Deserializer;

public class LogMessageSerde implements Serde<LogMessage> {

    @Override
    public Serializer<LogMessage> serializer() {
        return new LogMessageSerializer();
    }

    @Override
    public Deserializer<LogMessage> deserializer() {
        return new LogMessageDeserializer();
    }
}