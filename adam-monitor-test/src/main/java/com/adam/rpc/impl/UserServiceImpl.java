package com.adam.rpc.impl;

import com.adam.rpc.UserService;
import com.adam.rpc.dto.OrderRequest;
import com.adam.rpc.dto.OrderResponse;
import com.adam.rpc.dto.UserInfo;
import com.adam.trace.AdamTraceContext;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户服务实现 - 演示Dubbo RPC中的AID和TraceId透传
 */
@DubboService(version = "1.0.0", timeout = 30000)
@Service
public class UserServiceImpl implements UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);
    
    @Override
    public UserInfo getUserById(String userId) {
        logger.info("开始查询用户信息, userId: {}", userId);
        
        // 获取当前的AID和TraceId
        String currentAid = AdamTraceContext.getAid();
        String currentTraceId = AdamTraceContext.getTraceId();
        
        logger.info("Provider收到的AID: {}, TraceId: {}", currentAid, currentTraceId);
        
        // 模拟数据库查询延迟
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 构建用户信息
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setUserName("用户-" + userId);
        userInfo.setEmail(userId + "@example.com");
        userInfo.setPhone("138****" + userId.substring(Math.max(0, userId.length() - 4)));
        
        // 将接收到的AID和TraceId设置到响应中
        userInfo.setAid(currentAid);
        userInfo.setTraceId(currentTraceId);
        
        logger.info("查询用户信息完成: {}", userInfo);
        return userInfo;
    }
    
    @Override
    public OrderResponse createOrder(OrderRequest request) {
        logger.info("开始创建订单: {}", request);
        
        // 获取当前的AID和TraceId
        String currentAid = AdamTraceContext.getAid();
        String currentTraceId = AdamTraceContext.getTraceId();
        
        logger.info("Provider收到的AID: {}, TraceId: {}", currentAid, currentTraceId);
        
        // 验证请求参数
        if (request.getUserId() == null || request.getItems() == null || request.getItems().isEmpty()) {
            logger.warn("订单创建失败: 请求参数不完整");
            OrderResponse failureResponse = OrderResponse.failure("请求参数不完整");
            failureResponse.setAid(currentAid);
            failureResponse.setTraceId(currentTraceId);
            return failureResponse;
        }
        
        // 模拟订单创建过程
        try {
            // 模拟库存检查
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 300));
            
            // 模拟订单持久化
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 150));
            
            // 模拟支付处理
            Thread.sleep(ThreadLocalRandom.current().nextInt(200, 500));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("订单创建过程被中断");
            OrderResponse failureResponse = OrderResponse.failure("订单创建过程被中断");
            failureResponse.setAid(currentAid);
            failureResponse.setTraceId(currentTraceId);
            return failureResponse;
        }
        
        // 生成订单ID
        String orderId = "ORDER-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        // 创建成功响应
        OrderResponse response = OrderResponse.success(orderId, request.getUserId(), request.getTotalAmount());
        response.setEstimatedDeliveryTime("3-5个工作日");
        
        // 设置AID和TraceId到响应中
        response.setAid(currentAid);
        response.setTraceId(currentTraceId);
        
        logger.info("订单创建成功: {}", response);
        return response;
    }
    
    @Override
    public boolean sendNotification(String userId, String message) {
        logger.info("开始发送通知, userId: {}, message: {}", userId, message);
        
        // 获取当前的AID和TraceId
        String currentAid = AdamTraceContext.getAid();
        String currentTraceId = AdamTraceContext.getTraceId();
        
        logger.info("Provider收到的AID: {}, TraceId: {}", currentAid, currentTraceId);
        
        // 模拟通知发送过程
        try {
            // 模拟消息队列发送
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
            
            // 模拟推送服务调用
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 300));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("通知发送过程被中断");
            return false;
        }
        
        // 模拟成功率（95%成功率）
        boolean success = ThreadLocalRandom.current().nextDouble() < 0.95;
        
        if (success) {
            logger.info("通知发送成功, userId: {}, AID: {}, TraceId: {}", userId, currentAid, currentTraceId);
        } else {
            logger.warn("通知发送失败, userId: {}, AID: {}, TraceId: {}", userId, currentAid, currentTraceId);
        }
        
        return success;
    }
} 