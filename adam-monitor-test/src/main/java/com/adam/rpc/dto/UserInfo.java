package com.adam.rpc.dto;

import java.io.Serializable;

/**
 * 用户信息DTO
 */
public class UserInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private String userName;
    private String email;
    private String phone;
    private String traceId;
    private String aid;
    
    public UserInfo() {}
    
    public UserInfo(String userId, String userName, String email, String phone) {
        this.userId = userId;
        this.userName = userName;
        this.email = email;
        this.phone = phone;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(String userName) {
        this.userName = userName;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
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
    
    @Override
    public String toString() {
        return "UserInfo{" +
                "userId='" + userId + '\'' +
                ", userName='" + userName + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", traceId='" + traceId + '\'' +
                ", aid='" + aid + '\'' +
                '}';
    }
} 