package com.adam.listener;

import com.adam.entitys.LogMessage;
import com.adam.service.LogAnalyticalService;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;

/**
 * @author Mokai
 * @description 监听日志队列信息
 * @day 2024/7/12
 */
@Component
@Slf4j
public class MonitorLogListener {
    private static final Logger log = LoggerFactory.getLogger(MonitorLogListener.class);

    @Autowired
    private LogAnalyticalService analyticalService;


    @KafkaListener(topics = {"adam-monitor-logs"},groupId = "test")
    public void onMessage(ConsumerRecord<String, String> record) {
        Optional<String> kafkaMessage = Optional.ofNullable(record.value());
        if (kafkaMessage.isPresent()) {
            Object message = kafkaMessage.get();
            if (StringUtils.isNotBlank(message.toString())) {
                LogMessage logMessage = JSON.parseObject(message.toString(), LogMessage.class);
                log.info("接收kafka日志信息:{}", JSON.toJSONString(logMessage));
                analyticalService.saveAll(Collections.singletonList(JSON.toJSONString(logMessage)));
                log.info("日志消费完毕,traceId:{}",logMessage.getTraceId());
            }

        }
    }
}
