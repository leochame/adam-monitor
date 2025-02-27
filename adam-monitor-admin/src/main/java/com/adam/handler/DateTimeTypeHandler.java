package com.adam.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@MappedTypes(Long.class)
@MappedJdbcTypes(JdbcType.TIMESTAMP)
public class DateTimeTypeHandler extends BaseTypeHandler<Long> {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Long parameter, JdbcType jdbcType)
        throws SQLException {
        // 将毫秒转为秒级时间戳，并格式化为 DateTime 字符串
        Instant instant = Instant.ofEpochMilli(parameter);
        String formatted = FORMATTER.format(instant.atZone(ZoneId.of("UTC")));
        ps.setString(i, formatted);
    }

    @Override
    public Long getNullableResult(ResultSet resultSet, String s) throws SQLException {
        return 0L;
    }

    @Override
    public Long getNullableResult(ResultSet resultSet, int i) throws SQLException {
        return 0L;
    }

    @Override
    public Long getNullableResult(CallableStatement callableStatement, int i) throws SQLException {
        return 0L;
    }

    // getNullableResult 方法需反向解析（略）
}