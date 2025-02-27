package com.adam.mapper;

import com.adam.listener.LogMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


import java.util.List;

@Mapper
public interface LogAnalyseMapper {
    void batchInsert(@Param("list") List<LogMessage> logMessages);
}
