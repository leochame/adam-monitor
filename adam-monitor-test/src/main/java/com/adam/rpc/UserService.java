package com.adam.rpc;

import com.adam.rpc.dto.OrderRequest;
import com.adam.rpc.dto.OrderResponse;
import com.adam.rpc.dto.UserInfo;

/**
 * 用户服务接口 - 用于测试RPC调用中的AID和TraceId透传
 */
public interface UserService {
    
    /**
     * 根据用户ID获取用户信息
     * @param userId 用户ID
     * @return 用户信息
     */
    UserInfo getUserById(String userId);
    
    /**
     * 创建订单 - 测试复杂RPC调用场景
     * @param request 订单请求
     * @return 订单响应
     */
    OrderResponse createOrder(OrderRequest request);
    
    /**
     * 发送通知 - 测试异步场景
     * @param userId 用户ID
     * @param message 消息内容
     * @return 是否成功
     */
    boolean sendNotification(String userId, String message);
} 