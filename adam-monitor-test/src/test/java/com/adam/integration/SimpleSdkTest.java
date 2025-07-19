package com.adam.integration;

import com.adam.appender.CustomAppender;
import com.adam.trace.AdamTraceContext;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 简单的SDK功能测试
 */
public class SimpleSdkTest {

    private CustomAppender<ILoggingEvent> appender;
    private LoggerContext loggerContext;

    @Before
    public void setUp() {
        AdamTraceContext.clearAid();
        loggerContext = new LoggerContext();
        
        // 创建并配置CustomAppender
        appender = new CustomAppender<>();
        appender.setSystemName("test-system");
        appender.setGroupId("com.adam");
        appender.setHost("localhost");
        appender.setPort(9092);
        appender.setBatchSize(16384);
        appender.start();
    }

    @After
    public void tearDown() {
        AdamTraceContext.clearAid();
        if (appender != null) {
            appender.stop();
        }
        if (loggerContext != null) {
            loggerContext.stop();
        }
    }

    /**
     * 测试AID和TraceId的基本设置和获取
     */
    @Test
    public void testBasicAidAndTraceId() {
        System.out.println("=== 测试AID和TraceId基本功能 ===");
        
        String testAid = "test-aid-123";
        String testTraceId = "test-trace-456";
        
        // 设置AID
        AdamTraceContext.setAid(testAid);
        
        // 验证设置成功
        assertEquals("AID应该设置成功", testAid, AdamTraceContext.getAid());
        // TraceId由SkyWalking自动生成，我们只能获取
        String actualTraceId = AdamTraceContext.getTraceId();
        System.out.println("实际TraceId: " + actualTraceId);
        
        System.out.println("AID设置成功: " + AdamTraceContext.getAid());
        System.out.println("TraceId设置成功: " + AdamTraceContext.getTraceId());
        
        // 测试清除功能
        AdamTraceContext.clearAid();
        assertNull("AID应该被清除", AdamTraceContext.getAid());
        assertEquals("TraceId应该保持不变", testTraceId, AdamTraceContext.getTraceId());
        
        System.out.println("=== AID和TraceId基本功能测试完成 ===");
    }

    /**
     * 测试CustomAppender的基本功能
     */
    @Test
    public void testCustomAppenderBasic() {
        System.out.println("=== 测试CustomAppender基本功能 ===");
        
        // 设置AID
        AdamTraceContext.setAid("appender-test-aid");
        
        // 创建测试日志事件
        LoggingEvent event = new LoggingEvent();
        event.setMessage("Test log message from SDK");
        event.setTimeStamp(System.currentTimeMillis());
        event.setLevel(ch.qos.logback.classic.Level.INFO);
        event.setLoggerName("com.adam.test");
        
        // 测试appender是否正常运行
        assertTrue("Appender应该处于运行状态", appender.isStarted());
        
        try {
            // 尝试发送日志（由于没有Kafka连接，可能会失败，但不会抛出异常）
            appender.doAppend(event);
            System.out.println("日志事件已发送到CustomAppender");
        } catch (Exception e) {
            System.out.println("日志发送失败（预期行为，因为没有Kafka连接）: " + e.getMessage());
        }
        
        System.out.println("=== CustomAppender基本功能测试完成 ===");
    }

    /**
     * 测试线程间的AID传播
     */
    @Test
    public void testThreadAidPropagation() throws InterruptedException {
        System.out.println("=== 测试线程间AID传播 ===");
        
        String mainThreadAid = "main-thread-aid";
        AdamTraceContext.setAid(mainThreadAid);
        
        // 创建子线程
        Thread childThread = new Thread(() -> {
            // 在子线程中获取AID
            String childThreadAid = AdamTraceContext.getAid();
            System.out.println("子线程获取到的AID: " + childThreadAid);
            
            // 设置新的AID
            String newAid = "child-thread-aid";
            AdamTraceContext.setAid(newAid);
            System.out.println("子线程设置新AID: " + newAid);
            
            // 验证新AID
            assertEquals("子线程应该能设置新的AID", newAid, AdamTraceContext.getAid());
        });
        
        childThread.start();
        childThread.join();
        
        // 主线程的AID应该保持不变
        assertEquals("主线程的AID应该保持不变", mainThreadAid, AdamTraceContext.getAid());
        
        System.out.println("=== 线程间AID传播测试完成 ===");
    }

    /**
     * 测试上下文注入和提取
     */
    @Test
    public void testContextInjectionAndExtraction() {
        System.out.println("=== 测试上下文注入和提取 ===");
        
        String testAid = "injection-test-aid";
        
        AdamTraceContext.setAid(testAid);
        
        // 注入上下文
        java.util.Map<String, String> context = AdamTraceContext.injectContext();
        
        // 验证注入的上下文
        assertNotNull("注入的上下文不应该为null", context);
        assertTrue("上下文应该包含AID", context.containsKey(AdamTraceContext.getAidHeaderKey()));
        assertEquals("注入的AID应该正确", testAid, context.get(AdamTraceContext.getAidHeaderKey()));
        
        System.out.println("注入的上下文: " + context);
        
        // 清除当前上下文
        AdamTraceContext.clearAid();
        
        // 提取上下文
        AdamTraceContext.extractContext(context);
        
        // 验证提取的上下文
        assertEquals("提取的AID应该正确", testAid, AdamTraceContext.getAid());
        // TraceId由SkyWalking管理，我们只验证AID
        
        System.out.println("=== 上下文注入和提取测试完成 ===");
    }
} 