package com.adam.service;

import com.adam.listener.LogMessage;

import java.util.List;

/**
 * 分析日志
 */
public interface LogAnalyticalService {

    void saveAll(List<LogMessage> logBatch);
}
