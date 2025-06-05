package com.adam.service;

import com.adam.listener.LogMessage;
import com.adam.mapper.LogAnalyseMapper;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LogAnalyticalServiceImpl implements LogAnalyticalService {

    @Autowired
    private LogAnalyseMapper logAnalyseMapper;

    @Override
    public void saveAll(List<String> logBatch) {
        log.debug("接收到日志批次: {} 条", logBatch.size());
        
        List<LogMessage> logMessages = logBatch.stream()
                .map(json -> {
                    try {
                        LogMessage log = JSON.parseObject(json, LogMessage.class);
                        // 确保每条日志都有traceId和时间戳
                        if (log.getTraceId() == null || log.getTraceId().isEmpty()) {
                            log.setTraceId(UUID.randomUUID().toString());
                        }
                        if (log.getTimestamp() == null) {
                            log.setTimestamp(System.currentTimeMillis());
                        }
                        return log;
                    } catch (Exception e) {
                        log.error("日志反序列化失败: {}", json, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!logMessages.isEmpty()) {
            try {
                logAnalyseMapper.batchInsert(logMessages); // 批量插入ClickHouse
                log.info("成功保存 {} 条日志到ClickHouse", logMessages.size());
            } catch (Exception e) {
                log.error("保存日志到ClickHouse失败", e);
            }
        }
    }
}
