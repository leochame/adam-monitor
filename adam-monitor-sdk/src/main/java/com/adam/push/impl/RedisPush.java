package com.adam.push.impl;

import com.adam.entitys.LogMessage;
import com.adam.push.IPush;

import java.util.List;

// Redis 推送实现
public class RedisPush implements IPush {
    private String host;
    private int port;

    @Override
    public void open(String host, int port) {
        this.host = host;
        this.port = port;
        // 实现连接 Redis 的代码
        System.out.println("Connecting to Redis at " + host + ":" + port);
    }

    @Override
    public void send(LogMessage logMessage) {
        // 推送日志到 Redis
        System.out.println("Sending log to Redis: " + logMessage);
    }

    @Override
    public void sendBatch(List<LogMessage> logMessages) {

    }

    @Override
    public void close() {

    }
}
