package com.adam.push.impl;

import com.adam.entitys.LogMessage;
import com.adam.push.IPush;

// Kafka 推送实现
public class KafkaPush implements IPush {
    private String host;
    private int port;

    @Override
    public void open(String host, int port) {
        this.host = host;
        this.port = port;
        // 实现连接 Kafka 的代码
        System.out.println("Connecting to Kafka at " + host + ":" + port);
    }

    @Override
    public void send(LogMessage logMessage) {
        // 推送日志到 Kafka
        System.out.println("Sending log to Kafka: " + logMessage);
    }
}
