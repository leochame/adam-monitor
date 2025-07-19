package com.adam;

import com.adam.trace.AdamTraceContext;

/**
 * 简化的SDK测试
 */
public class SimpleTest {
    
    public static void main(String[] args) {
        System.out.println("=== 简化SDK测试 ===");
        
        try {
            // 测试1: AID基本功能
            System.out.println("\n--- 测试AID基本功能 ---");
            String testAid = "test-aid-" + System.currentTimeMillis();
            
            // 设置AID
            AdamTraceContext.setAid(testAid);
            System.out.println("设置AID: " + testAid);
            
            // 获取AID
            String actualAid = AdamTraceContext.getAid();
            System.out.println("获取AID: " + actualAid);
            
            if (testAid.equals(actualAid)) {
                System.out.println("✅ AID设置和获取功能正常");
            } else {
                System.out.println("❌ AID设置和获取功能异常");
            }
            
            // 测试2: 上下文注入
            System.out.println("\n--- 测试上下文注入 ---");
            java.util.Map<String, String> context = AdamTraceContext.injectContext();
            System.out.println("注入的上下文: " + context);
            
            if (context != null && context.containsKey(AdamTraceContext.getAidHeaderKey())) {
                System.out.println("✅ 上下文注入功能正常");
            } else {
                System.out.println("❌ 上下文注入功能异常");
            }
            
            // 测试3: 清除功能
            System.out.println("\n--- 测试清除功能 ---");
            AdamTraceContext.clearAid();
            String clearedAid = AdamTraceContext.getAid();
            System.out.println("清除后AID: " + clearedAid);
            
            if (clearedAid == null) {
                System.out.println("✅ AID清除功能正常");
            } else {
                System.out.println("❌ AID清除功能异常");
            }
            
            System.out.println("\n=== 简化测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("❌ 测试过程中发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 