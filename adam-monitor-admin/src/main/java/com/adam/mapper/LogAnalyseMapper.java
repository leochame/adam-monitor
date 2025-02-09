package com.adam.mapper;

import com.adam.listener.LogMessage;
import jodd.introspector.Mapper;


import java.util.List;

public interface LogAnalyseMapper {
    void saveAll(List<LogMessage> logMessages);
}
