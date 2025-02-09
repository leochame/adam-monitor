package com.adam.service;

import com.adam.listener.LogMessage;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogAnalyticalServiceImpl implements LogAnalyticalService {

    @Override
    public void saveAll(List<LogMessage> logBatch) {

    }
}
