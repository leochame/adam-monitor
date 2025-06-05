# ClickHouse日志存储方案

## 概述

本文档描述了如何使用ClickHouse存储日志数据，以及如何移除Redis依赖。

## ClickHouse配置

### 表结构

```sql
CREATE TABLE IF NOT EXISTS log_table
(
    id UInt64,
    trace_id String,
    system_name String,
    class_name String,
    method_name String,
    content String,
    timestamp DateTime
)
ENGINE = MergeTree()
PRIMARY KEY (id)
ORDER BY (timestamp, system_name);
```

### 连接配置

在`application.properties`中配置ClickHouse连接：

```properties
# ClickHouse配置
spring.datasource.type=com.alibaba.druid.pool.DruidDataSource
spring.datasource.driver-class-name=com.clickhouse.jdbc.ClickHouseDriver
spring.datasource.url=jdbc:clickhouse:http://localhost:8123/default
spring.datasource.username=