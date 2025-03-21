package com.adam.push;

import com.adam.entitys.LogMessage;

import java.util.List;

// 推送接口
public interface IPush {
    /**
     * 初始化连接
     * @param host IP地址
     * @param port 端口号
     */
    void open(String host, int port);
    void send(LogMessage logMessage);
    void sendBatch(List<LogMessage> logMessages);

    void close();
}
