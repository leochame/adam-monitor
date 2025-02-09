package com.adam.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.adam.entitys.LogMessage;
import com.adam.push.IPush;
import com.adam.push.PushFactory;

import java.util.Arrays;

// 自定义日志采集器
public class CustomAppender<E> extends UnsynchronizedAppenderBase<E> {

    private String systemName;
    private String groupId;
    private String host;
    private int port;
    private IPush push;  // 推送接口

    @Override
    protected void append(E eventObject) {
        if (push == null) {
            throw new IllegalStateException("Push strategy is not initialized");
        }

        if (eventObject instanceof ILoggingEvent) {
            ILoggingEvent event = (ILoggingEvent) eventObject;
            String methodName = "unknown";
            String className = "unknown";
            StackTraceElement[] callerDataArray = event.getCallerData();
            if (callerDataArray != null && callerDataArray.length > 0) {
                StackTraceElement callerData = callerDataArray[0];
                methodName = callerData.getMethodName();
                className = callerData.getClassName();
            }

            // 包名过滤：仅处理指定 groupId 开头的类
            if (!className.startsWith(groupId)) {
                return;
            }

            // 构建日志消息
            LogMessage logMessage = new LogMessage(systemName,
                    className,
                    methodName,
                    Arrays.asList(event.getFormattedMessage().split(" "))
            );

            // 推送日志消息
            push.send(logMessage);
        }
    }

    // 使用工厂来设置推送类型
    public void setPushType(String pushType) {
        this.push = PushFactory.createPush(pushType, host, port);  // 使用工厂创建推送对象
    }

    // 设置其他配置参数（例如系统名称、日志采集范围等）
    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
