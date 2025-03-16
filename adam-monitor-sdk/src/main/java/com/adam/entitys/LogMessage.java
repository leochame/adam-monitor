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
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }
    
    public void setClassName(String className) {
        this.className = className;
    }
    
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
}
