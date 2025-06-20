package com.adam.trace;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Adam业务上下文管理器
 * <p>
 * 提供统一的AID（业务ID）管理功能：
 * 1. 支持手动设置和获取AID
 * 2. 优先使用SkyWalking传递，ThreadLocal作为降级方案
 * 3. 提供上下文注入和提取功能，支持RPC和MQ自动透传
 */
public class AdamTraceContext {

    private static final Logger logger = LoggerFactory.getLogger(AdamTraceContext.class);
    private static final String AID_KEY = "AID";
    private static final String HEADER_AID_KEY = "X-Adam-AID";
    private static final ThreadLocal<String> LOCAL_AID = new ThreadLocal<>();
    private static volatile boolean skywalkingAvailable = false;
    
    static {
        // 初始化时检查SkyWalking可用性
        checkSkyWalkingAvailability();
    }

    /**
     * 检查SkyWalking Agent是否可用
     */
    private static void checkSkyWalkingAvailability() {
        try {
            String traceId = TraceContext.traceId();
            skywalkingAvailable = traceId != null && !traceId.isEmpty() && !"N/A".equals(traceId);
            if (skywalkingAvailable) {
                logger.info("SkyWalking Agent detected, using SkyWalking for AID propagation");
            } else {
                logger.info("SkyWalking Agent not detected, using ThreadLocal as fallback");
            }
        } catch (Throwable e) {
            logger.warn("Failed to check SkyWalking availability, using ThreadLocal fallback", e);
            skywalkingAvailable = false;
        }
    }

    /**
     * 设置业务ID（AID）
     * 在请求开始时手动调用此方法设置AID
     *
     * @param aid 业务ID
     */
    public static void setAid(String aid) {
        if (aid == null || aid.trim().isEmpty()) {
            return;
        }
        
        String trimmedAid = aid.trim();
        
        if (skywalkingAvailable) {
            try {
                TraceContext.putCorrelation(AID_KEY, trimmedAid);
                logger.debug("Set AID in SkyWalking context: {}", trimmedAid);
            } catch (Throwable e) {
                logger.warn("Failed to set AID in SkyWalking, fallback to ThreadLocal", e);
                LOCAL_AID.set(trimmedAid);
            }
        } else {
            LOCAL_AID.set(trimmedAid);
            logger.debug("Set AID in ThreadLocal: {}", trimmedAid);
        }
    }

    /**
     * 获取当前业务ID（AID）
     *
     * @return 业务ID，如果不存在则返回null
     */
    public static String getAid() {
        if (skywalkingAvailable) {
            try {
                return TraceContext.getCorrelation(AID_KEY).orElse(null);
            } catch (Throwable e) {
                logger.warn("Failed to get AID from SkyWalking, fallback to ThreadLocal", e);
            }
        }
        return LOCAL_AID.get();
    }

    /**
     * 清除当前业务ID（AID）
     */
    public static void clearAid() {
        if (skywalkingAvailable) {
            try {
                TraceContext.putCorrelation(AID_KEY, "");
            } catch (Throwable e) {
                logger.warn("Failed to clear AID in SkyWalking", e);
            }
        }
        LOCAL_AID.remove();
        logger.debug("Cleared AID from context");
    }

    /**
     * 注入上下文到Map中（用于RPC和MQ）
     *
     * @return 包含AID的上下文Map
     */
    public static Map<String, String> injectContext() {
        Map<String, String> context = new HashMap<>();
        String aid = getAid();
        if (aid != null && !aid.isEmpty()) {
            context.put(HEADER_AID_KEY, aid);
            logger.debug("Injected AID to context: {}", aid);
        }
        return context;
    }

    /**
     * 从Map中提取上下文（用于RPC和MQ）
     *
     * @param context 包含上下文信息的Map
     */
    public static void extractContext(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return;
        }
        
        String aid = context.get(HEADER_AID_KEY);
        if (aid != null && !aid.trim().isEmpty()) {
            setAid(aid.trim());
            logger.debug("Extracted AID from context: {}", aid);
        }
    }

    /**
     * 检查SkyWalking是否可用
     *
     * @return true如果SkyWalking可用
     */
    public static boolean isSkywalkingAvailable() {
        return skywalkingAvailable;
    }

    /**
     * 获取TraceId（仅在SkyWalking可用时）
     *
     * @return TraceId或null
     */
    public static String getTraceId() {
        if (skywalkingAvailable) {
            try {
                return TraceContext.traceId();
            } catch (Throwable e) {
                logger.warn("Failed to get TraceId from SkyWalking", e);
            }
        }
        return null;
    }

    /**
     * 获取AID的Header键名
     *
     * @return Header键名
     */
    public static String getAidHeaderKey() {
        return HEADER_AID_KEY;
    }
}