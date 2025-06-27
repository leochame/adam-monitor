package com.adam.trace;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * AdamTraceContext功能演示程序
 * 验证AID透传的核心功能
 */
public class AdamTraceContextDemo {

    public static void main(String[] args) {
        System.out.println("=== AdamTraceContext 功能演示 ===");
        
        // 1. 基本功能测试
        testBasicFunctions();
        
        // 2. 上下文注入和提取测试
        testContextOperations();
        
        // 3. 线程隔离测试
        testThreadIsolation();
        
        // 4. 系统信息测试
        testSystemInfo();
        
        System.out.println("\n=== 演示完成 ===");
    }
    
    /**
     * 测试基本功能
     */
    private static void testBasicFunctions() {
        System.out.println("\n--- 基本功能测试 ---");
        
        try {
            // 清除之前的AID
            AdamTraceContext.clearAid();
            System.out.println("✓ clearAid() 调用成功");
            
            // 设置AID
            String testAid = "demo-aid-123";
            AdamTraceContext.setAid(testAid);
            System.out.println("✓ setAid(\"" + testAid + "\") 调用成功");
            
            // 获取AID
            String retrievedAid = AdamTraceContext.getAid();
            System.out.println("✓ getAid() 调用成功，返回值: " + retrievedAid);
            
            // 验证AID是否正确传递
            if (testAid.equals(retrievedAid)) {
                System.out.println("✓ AID传递成功！");
            } else {
                System.out.println("! AID传递结果: 设置=" + testAid + ", 获取=" + retrievedAid);
                System.out.println("  (在没有SkyWalking Agent的环境中，这是正常现象)");
            }
            
        } catch (Exception e) {
            System.err.println("✗ 基本功能测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试上下文操作
     */
    private static void testContextOperations() {
        System.out.println("\n--- 上下文操作测试 ---");
        
        try {
            // 设置AID
            String contextAid = "context-aid-456";
            AdamTraceContext.setAid(contextAid);
            
            // 测试上下文注入
            Map<String, String> context = AdamTraceContext.injectContext();
            System.out.println("✓ injectContext() 调用成功");
            System.out.println("  注入的上下文: " + context);
            
            // 测试上下文提取
            Map<String, String> extractContext = new HashMap<>();
            extractContext.put(AdamTraceContext.getAidHeaderKey(), "extract-aid-789");
            
            AdamTraceContext.extractContext(extractContext);
            System.out.println("✓ extractContext() 调用成功");
            
            String extractedAid = AdamTraceContext.getAid();
            System.out.println("  提取后的AID: " + extractedAid);
            
        } catch (Exception e) {
            System.err.println("✗ 上下文操作测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试线程隔离
     */
    private static void testThreadIsolation() {
        System.out.println("\n--- 线程隔离测试 ---");
        
        try {
            // 在主线程设置AID
            String mainThreadAid = "main-thread-aid";
            AdamTraceContext.setAid(mainThreadAid);
            System.out.println("主线程设置AID: " + mainThreadAid);
            
            ExecutorService executor = Executors.newSingleThreadExecutor();
            
            Future<String> future = executor.submit(() -> {
                // 在子线程中获取AID
                String childThreadAid = AdamTraceContext.getAid();
                System.out.println("子线程获取AID: " + childThreadAid);
                return childThreadAid;
            });
            
            String childAid = future.get(5, TimeUnit.SECONDS);
            String currentMainAid = AdamTraceContext.getAid();
            
            System.out.println("主线程当前AID: " + currentMainAid);
            System.out.println("子线程返回AID: " + childAid);
            
            if (mainThreadAid.equals(childAid)) {
                System.out.println("✓ AID成功跨线程传播（SkyWalking生效）");
            } else {
                System.out.println("! AID未跨线程传播（ThreadLocal隔离，正常现象）");
            }
            
            executor.shutdown();
            
        } catch (Exception e) {
            System.err.println("✗ 线程隔离测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 测试系统信息
     */
    private static void testSystemInfo() {
        System.out.println("\n--- 系统信息测试 ---");
        
        try {
            // 获取Header键名
            String headerKey = AdamTraceContext.getAidHeaderKey();
            System.out.println("AID Header键名: " + headerKey);
            
            // 检查SkyWalking可用性
            boolean isSkywalkingAvailable = AdamTraceContext.isSkywalkingAvailable();
            System.out.println("SkyWalking可用性: " + isSkywalkingAvailable);
            
            // 获取TraceId
            String traceId = AdamTraceContext.getTraceId();
            System.out.println("当前TraceId: " + traceId);
            
            if (isSkywalkingAvailable) {
                System.out.println("✓ SkyWalking Agent已启用，支持完整的链路追踪功能");
            } else {
                System.out.println("! SkyWalking Agent未启用，使用ThreadLocal降级方案");
            }
            
        } catch (Exception e) {
            System.err.println("✗ 系统信息测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}