package com.adam.entitys;

/**
 * 日志消息
 */
public class LogMessage {

    private String traceId;

    private String systemName;

    private String className;

    private String methodName;

    private String content;

    private Long timestamp;

    public LogMessage(String systemName, String className, String methodName, String formattedMessage, Long ntpTime) {
        this.systemName = systemName;
        this.className = className;
        this.methodName = methodName;
        this.content = formattedMessage;
        this.timestamp = ntpTime;
    }

    public LogMessage(String systemName, String className, String methodName, String content) {
        this.systemName = systemName;
        this.className = className;
        this.methodName = methodName;
        this.content = content;
    }

    public String getSystemName() {
        return systemName;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getContent() {
        return content;
    }

    public Long getTimestamp(){return timestamp;}
}
