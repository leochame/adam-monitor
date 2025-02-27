package com.adam.listener;

import lombok.Data;
import lombok.Getter;

import java.util.List;

/**
 * 日志消息
 */
@Data
public class LogMessage {

    private String traceId;

    private String systemName;

    private String className;

    private String methodName;

    private String content;

    private Long timestamp;

    public LogMessage() {
    }

    public LogMessage(String systemName, String className, String methodName, String content) {
        this.systemName = systemName;
        this.className = className;
        this.methodName = methodName;
        this.content = content;
    }

}
