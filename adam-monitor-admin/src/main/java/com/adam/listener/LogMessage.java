package com.adam.listener;

import lombok.Data;

import java.util.List;
@Data
public class LogMessage {
    private String systemName;
    private String className;
    private String methodName;
    private List<String> logContent;

    // Constructor
    public LogMessage(String systemName, String className, String methodName, List<String> logContent) {
        this.systemName = systemName;
        this.className = className;
        this.methodName = methodName;
        this.logContent = logContent;
    }
}