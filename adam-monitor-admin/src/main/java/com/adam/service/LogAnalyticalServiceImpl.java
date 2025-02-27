package com.adam.service;

import com.adam.listener.LogMessage;
import com.adam.mapper.LogAnalyseMapper;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.apache.kafka.streams.kstream.EmitStrategy.log;

@Service
public class LogAnalyticalServiceImpl implements LogAnalyticalService {

    @Autowired
    private LogAnalyseMapper logAnalyseMapper;

    @Override
    public void saveAll(List<String> logBatch) {
        System.out.println(logBatch);
        List<LogMessage> logMessages = logBatch.stream()
                .map(json -> {
                    try {
                        LogMessage log = JSON.parseObject(json, LogMessage.class);
                        return log;// FastJSON 反序列化
                    } catch (Exception e) {
                        log.error("日志反序列化失败: {}", json);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (!logMessages.isEmpty()) {
            logAnalyseMapper.batchInsert(logMessages); // 调用 MyBatis 批量插入
        }
    }
}
