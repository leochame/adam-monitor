package com.adam.listener;

import com.adam.service.LogAnalyticalService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Optional;

/**
 * Kafka日志监听器
 * 用于替代Redis接收SDK发送的日志消息
 */
@Component
public class KafkaLogListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaLogListener.class);

    @Autowired
    private LogAnalyticalService analyticalService;

    @KafkaListener(topics = {"adam-monitor-logs-test"},groupId = "test")
    public void onMessage(ConsumerRecord<String, String> record) {
        log.info("start consumer log");
        Optional<String> kafkaMessage = Optional.ofNullable(record.value());
        if (kafkaMessage.isPresent()) {
            Object message = kafkaMessage.get();
            if (StringUtils.isNotBlank(message.toString())) {
                analyticalService.saveAll(Collections.singletonList(message.toString()));
            }
        }
    }
}