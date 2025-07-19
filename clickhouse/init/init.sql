-- 创建数据库
CREATE DATABASE IF NOT EXISTS adam_monitor;

-- 切换到数据库
USE adam_monitor;

-- 创建日志表
CREATE TABLE IF NOT EXISTS log_messages (
    id UUID DEFAULT generateUUIDv4(),
    trace_id String,
    aid String COMMENT '业务ID',
    system_name String,
    class_name String,
    method_name String,
    content String,
    timestamp DateTime64(3) DEFAULT now(),
    level String DEFAULT 'INFO',
    thread_name String DEFAULT '',
    logger_name String DEFAULT '',
    INDEX idx_trace_id trace_id TYPE bloom_filter GRANULARITY 1,
    INDEX idx_aid aid TYPE bloom_filter GRANULARITY 1,
    INDEX idx_system system_name TYPE bloom_filter GRANULARITY 1
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, system_name, trace_id)
TTL timestamp + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;

-- 创建性能监控表
CREATE TABLE IF NOT EXISTS performance_metrics (
    id UUID DEFAULT generateUUIDv4(),
    metric_name String,
    metric_value Float64,
    tags Map(String, String),
    timestamp DateTime64(3) DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, metric_name)
TTL timestamp + INTERVAL 7 DAY
SETTINGS index_granularity = 8192;
