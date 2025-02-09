package com.adam;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.core.util.StatusPrinter;
import com.adam.appender.CustomAppender;
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
        // 创建CustomAppender实例
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
        for (int i = 0; i < 1000; i++) {
            logger.info("Test message userId:{},state:{}", i,i);
        }

        // 等待一段时间，确保日志已被处理
        TimeUnit.SECONDS.sleep(5);

        // 停止appender
        appender.stop();
    }

    // 模拟创建ILoggingEvent
    private static ILoggingEvent createMockLoggingEvent(String message) {
        return new ILoggingEvent() {

            @Override
            public String getThreadName() {
                return "";
            }

            @Override
            public Level getLevel() {
                return null;
            }

            @Override
            public String getMessage() {
                return "";
            }

            @Override
            public Object[] getArgumentArray() {
                return new Object[0];
            }

            @Override
            public String getFormattedMessage() {
                return "";
            }

            @Override
            public String getLoggerName() {
                return "";
            }

            @Override
            public LoggerContextVO getLoggerContextVO() {
                return null;
            }

            @Override
            public IThrowableProxy getThrowableProxy() {
                return null;
            }

            @Override
            public StackTraceElement[] getCallerData() {
                return new StackTraceElement[0];
            }

            @Override
            public boolean hasCallerData() {
                return false;
            }

            @Override
            public List<Marker> getMarkerList() {
                return List.of();
            }

            @Override
            public Map<String, String> getMDCPropertyMap() {
                return Map.of();
            }

            @Override
            public Map<String, String> getMdc() {
                return Map.of();
            }

            @Override
            public long getTimeStamp() {
                return 0;
            }

            @Override
            public int getNanoseconds() {
                return 0;
            }

            @Override
            public long getSequenceNumber() {
                return 0;
            }

            @Override
            public List<KeyValuePair> getKeyValuePairs() {
                return List.of();
            }

            @Override
            public void prepareForDeferredProcessing() {

            }
        };
    }
}
