package com.adam.trace;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * AdamTraceContext 简单单元测试
 * 测试核心的AID设置、获取、清除功能
 */
public class AdamTraceContextSimpleTest {

    @Before
    public void setUp() {
        // 清理测试环境
        AdamTraceContext.clearAid();
    }

    @After
    public void tearDown() {
        // 清理测试环境
        AdamTraceContext.clearAid();
    }

    /**
     * 测试基本的AID设置和获取功能
     */
    @Test
    public void testBasicSetAndGet() {
        String testAid = "test-aid-123";
        
        // 设置AID
        AdamTraceContext.setAid(testAid);
        
        // 获取AID
        String retrievedAid = AdamTraceContext.getAid();
        
        // 验证
        assertEquals("设置的AID应该能正确获取", testAid, retrievedAid);
    }

    /**
     * 测试AID清除功能
     */
    @Test
    public void testClearAid() {
        String testAid = "test-aid-456";
        
        // 设置AID
        AdamTraceContext.setAid(testAid);
        assertNotNull("设置后AID不应该为null", AdamTraceContext.getAid());
        
        // 清除AID
        AdamTraceContext.clearAid();
        
        // 验证清除结果
        String clearedAid = AdamTraceContext.getAid();
        assertTrue("清除后AID应该为null或空字符串", 
                  clearedAid == null || clearedAid.trim().isEmpty());
    }

    /**
     * 测试SkyWalking可用性检查
     */
    @Test
    public void testSkyWalkingAvailability() {
        boolean isAvailable = AdamTraceContext.isSkywalkingAvailable();
        
        // 在测试环境中，SkyWalking通常不可用
        System.out.println("SkyWalking可用性: " + isAvailable);
        
        // 验证方法不会抛出异常
        assertTrue("SkyWalking可用性检查应该返回boolean值", 
                  isAvailable == true || isAvailable == false);
    }

    /**
     * 测试AID Header键名获取
     */
    @Test
    public void testGetAidHeaderKey() {
        String headerKey = AdamTraceContext.getAidHeaderKey();
        
        assertNotNull("Header键名不应该为null", headerKey);
        assertFalse("Header键名不应该为空", headerKey.trim().isEmpty());
        assertEquals("Header键名应该是X-Adam-AID", "X-Adam-AID", headerKey);
    }
}