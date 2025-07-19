package com.adam.rpc.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 订单请求DTO
 */
public class OrderRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String userId;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private String deliveryAddress;
    private String traceId;
    private String aid;
    
    public OrderRequest() {}
    
    public OrderRequest(String userId, List<OrderItem> items, BigDecimal totalAmount, String deliveryAddress) {
        this.userId = userId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.deliveryAddress = deliveryAddress;
    }
    
    // Getters and Setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public List<OrderItem> getItems() {
        return items;
    }
    
    public void setItems(List<OrderItem> items) {
        this.items = items;
    }
    
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }
    
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
    
    public String getDeliveryAddress() {
        return deliveryAddress;
    }
    
    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
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
        return "OrderRequest{" +
                "userId='" + userId + '\'' +
                ", items=" + items +
                ", totalAmount=" + totalAmount +
                ", deliveryAddress='" + deliveryAddress + '\'' +
                ", traceId='" + traceId + '\'' +
                ", aid='" + aid + '\'' +
                '}';
    }
    
    /**
     * 订单项内部类
     */
    public static class OrderItem implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
        
        public OrderItem() {}
        
        public OrderItem(String productId, String productName, Integer quantity, BigDecimal price) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.price = price;
        }
        
        // Getters and Setters
        public String getProductId() {
            return productId;
        }
        
        public void setProductId(String productId) {
            this.productId = productId;
        }
        
        public String getProductName() {
            return productName;
        }
        
        public void setProductName(String productName) {
            this.productName = productName;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
        
        public BigDecimal getPrice() {
            return price;
        }
        
        public void setPrice(BigDecimal price) {
            this.price = price;
        }
        
        @Override
        public String toString() {
            return "OrderItem{" +
                    "productId='" + productId + '\'' +
                    ", productName='" + productName + '\'' +
                    ", quantity=" + quantity +
                    ", price=" + price +
                    '}';
        }
    }
} 