-- 创建ClickHouse日志表
CREATE TABLE IF NOT EXISTS log_table
(
    id UInt64 AUTO_INCREMENT,
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

-- 创建索引以提高查询性能
ALTER TABLE log_table ADD INDEX idx_trace_id (trace_id) TYPE minmax GRANULARITY 3;
ALTER TABLE log_table ADD INDEX idx_system_name (system_name) TYPE minmax GRANULARITY 3;