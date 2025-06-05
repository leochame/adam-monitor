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