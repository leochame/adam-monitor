package com.adam;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.core.util.StatusPrinter;
import com.adam.appender.CustomAppender;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.KeyValuePair;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CustomAppenderTest {
    private static final Logger logger = LoggerFactory.getLogger(CustomAppenderTest.class);

    public static void main(String[] args) throws InterruptedException {
        System.out.println(1);
        // 创建CustomAppendeKStream<String, String> stream = streamsBuilder.stream("itcast-topic-input");r实例
        CustomAppender<ILoggingEvent> appender = new CustomAppender<>();

        // 配置appender
        appender.setHost("localhost");
        appender.setPort(9092);
        appender.setPushType("Kafka");
        appender.setQueueCapacity(10000);
        appender.setBatchSize(200);
        appender.setMaxShards(8);
        appender.start();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        StatusPrinter.print(context);

        // 创建模拟日志事件
        for (int i = 0; i < 100000; i++) {
            logger.info("Test message userId:{}_{}_{}", i,i, JSON.toJSON(i));
        }
        Thread.sleep(2000);
        for (int i = 0; i < 100000; i++) {
            logger.info("Test message userId:{}_{}_{}", i,i, JSON.toJSON(i));
        }

        // 等待一段时间，确保日志已被处理
        TimeUnit.SECONDS.sleep(5);

        // 停止appender
        appender.stop();
    }
}
