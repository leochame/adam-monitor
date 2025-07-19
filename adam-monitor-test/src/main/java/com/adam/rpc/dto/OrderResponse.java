package com.adam.rpc.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单响应DTO
 */
public class OrderResponse implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String orderId;
    private String userId;
    private String status;
    private BigDecimal totalAmount;
    private LocalDateTime createdTime;
    private String estimatedDeliveryTime;
    private String traceId;
    private String aid;
    private boolean success;
    private String message;
    
    public OrderResponse() {}
    
    public OrderResponse(String orderId, String userId, String status, BigDecimal totalAmount) {
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdTime = LocalDateTime.now();
        this.success = true;
    }
    
    // 创建成功响应的静态方法
    public static OrderResponse success(String orderId, String userId, BigDecimal totalAmount) {
        OrderResponse response = new OrderResponse();
        response.setOrderId(orderId);
        response.setUserId(userId);
        response.setStatus("CREATED");
        response.setTotalAmount(totalAmount);
        response.setCreatedTime(LocalDateTime.now());
        response.setSuccess(true);
        response.setMessage("订单创建成功");
        return response;
    }
    
    // 创建失败响应的静态方法
    public static OrderResponse failure(String message) {
        OrderResponse response = new OrderResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setStatus("FAILED");
        return response;
    }
    
    // Getters and Setters
    public String getOrderId() {
        return orderId;
    }
    
    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public LocalDateTime getCreatedTime() {
        return createdTime;
    }
    
    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }
    
    public String getEstimatedDeliveryTime() {
        return estimatedDeliveryTime;
    }
    
    public void setEstimatedDeliveryTime(String estimatedDeliveryTime) {
        this.estimatedDeliveryTime = estimatedDeliveryTime;
    }
    
    public String getTraceId() {
        return traceId;
    }
    
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
    
    public String getAid() {
        return aid;
    }
    
    public void setAid(String aid) {
        this.aid = aid;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    @Override
    public String toString() {
        return "OrderResponse{" +
                "orderId='" + orderId + '\'' +
                ", userId='" + userId + '\'' +
                ", status='" + status + '\'' +
                ", totalAmount=" + totalAmount +
                ", createdTime=" + createdTime +
                ", estimatedDeliveryTime='" + estimatedDeliveryTime + '\'' +
                ", traceId='" + traceId + '\'' +
                ", aid='" + aid + '\'' +
                ", success=" + success +
                ", message='" + message + '\'' +
                '}';
    }
} 