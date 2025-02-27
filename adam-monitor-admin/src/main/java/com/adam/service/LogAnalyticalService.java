package com.adam.service;
import java.util.List;

/**
 * 分析日志
 */
public interface LogAnalyticalService {

    void saveAll(List<String> logBatch);
}
