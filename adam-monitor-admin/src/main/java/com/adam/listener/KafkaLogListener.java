package com.adam.listener;

import com.adam.service.LogAnalyticalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kafka日志监听器
 * 用于替代Redis接收SDK发送的日志消息
 */
@Component
@Slf4j
public class KafkaLogListener {

    @Autowired
    private LogAnalyticalService logAnalyticalService;

    @Autowired
    private MonitorLogListener monitorLogListener;

    /**
     * 批量接收Kafka消息
     * @param messages 消息列表
     */
    @KafkaListener(topics = "${spring.kafka.topic}", groupId = "${spring.kafka.group-id}")
    public void listen(List<String> messages) {
        log.debug("接收到Kafka消息批次: {} 条", messages.size());
        
        try {
            // 1. 直接将消息传递给MonitorLogListener处理
            for (String message : messages) {
                monitorLogListener.addLogMessage(message);
            }
            
            log.debug("成功处理 {} 条Kafka消息", messages.size());
        } catch (Exception e) {
            log.error("处理Kafka消息失败", e);
        }
    }
}