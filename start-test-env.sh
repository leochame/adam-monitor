#!/bin/bash

# Adam Monitor 测试环境启动脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查Docker和Docker Compose
check_prerequisites() {
    log_info "检查系统依赖..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker未安装，请先安装Docker"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose未安装，请先安装Docker Compose"
        exit 1
    fi
    
    # 检查Docker是否运行
    if ! docker info &> /dev/null; then
        log_error "Docker服务未运行，请启动Docker服务"
        exit 1
    fi
    
    log_success "系统依赖检查通过"
}

# 创建必要的目录
create_directories() {
    log_info "创建必要的目录..."
    
    directories=(
        "zk_data" "zk_log" "kafka_data"
        "nacos/logs" "nacos/data" "nacos/mysql_data" "nacos/mysql_init"
        "ch_data" "ch_logs" "clickhouse/init"
        "redis_data" "prometheus_data" "grafana_data"
    )
    
    for dir in "${directories[@]}"; do
        mkdir -p "$dir"
        log_info "创建目录: $dir"
    done
    
    log_success "目录创建完成"
}

# 创建Nacos初始化SQL
create_nacos_init_sql() {
    log_info "创建Nacos初始化SQL..."
    
    cat > nacos/mysql_init/nacos.sql << 'EOF'
CREATE DATABASE IF NOT EXISTS nacos DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;

USE nacos;

-- 创建Nacos配置表
CREATE TABLE IF NOT EXISTS `config_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'id',
  `data_id` varchar(255) NOT NULL COMMENT 'data_id',
  `group_id` varchar(255) DEFAULT NULL,
  `content` longtext NOT NULL COMMENT 'content',
  `md5` varchar(32) DEFAULT NULL COMMENT 'md5',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  `src_user` text COMMENT 'source user',
  `src_ip` varchar(50) DEFAULT NULL COMMENT 'source ip',
  `app_name` varchar(128) DEFAULT NULL,
  `tenant_id` varchar(128) DEFAULT '' COMMENT '租户字段',
  `c_desc` varchar(256) DEFAULT NULL,
  `c_use` varchar(64) DEFAULT NULL,
  `effect` varchar(64) DEFAULT NULL,
  `type` varchar(64) DEFAULT NULL,
  `c_schema` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_configinfo_datagrouptenant` (`data_id`,`group_id`,`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin COMMENT='config_info';

-- 更多Nacos必要的表结构...
EOF
    
    log_success "Nacos初始化SQL创建完成"
}

# 创建ClickHouse初始化SQL
create_clickhouse_init_sql() {
    log_info "创建ClickHouse初始化SQL..."
    
    cat > clickhouse/init/init.sql << 'EOF'
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
EOF
    
    log_success "ClickHouse初始化SQL创建完成"
}

# 启动基础设施服务
start_infrastructure() {
    log_info "启动基础设施服务..."
    
    # 启动基础服务（按依赖顺序）
    log_info "启动Zookeeper..."
    docker-compose -f docker-compose-test.yml up -d zookeeper
    
    log_info "等待Zookeeper启动..."
    wait_for_service "zookeeper" "2181"
    
    log_info "启动Nacos MySQL..."
    docker-compose -f docker-compose-test.yml up -d nacos-mysql
    
    log_info "等待MySQL启动..."
    wait_for_service "nacos-mysql" "3306"
    
    log_info "启动Nacos..."
    docker-compose -f docker-compose-test.yml up -d nacos
    
    log_info "等待Nacos启动..."
    wait_for_service "nacos" "8848"
    
    log_info "启动Kafka..."
    docker-compose -f docker-compose-test.yml up -d kafka
    
    log_info "等待Kafka启动..."
    wait_for_service "kafka" "9092"
    
    log_info "启动ClickHouse..."
    docker-compose -f docker-compose-test.yml up -d clickhouse
    
    log_info "启动Redis..."
    docker-compose -f docker-compose-test.yml up -d redis
    
    log_info "启动监控服务..."
    docker-compose -f docker-compose-test.yml up -d prometheus grafana jaeger
    
    log_info "启动Kafka UI..."
    docker-compose -f docker-compose-test.yml up -d kafka-ui
    
    log_success "基础设施服务启动完成"
}

# 等待服务启动
wait_for_service() {
    local service_name=$1
    local port=$2
    local max_attempts=30
    local attempt=0
    
    log_info "等待 $service_name 服务启动..."
    
    while [ $attempt -lt $max_attempts ]; do
        if nc -z localhost $port &> /dev/null; then
            log_success "$service_name 服务已启动"
            return 0
        fi
        
        attempt=$((attempt + 1))
        log_info "等待 $service_name 启动... ($attempt/$max_attempts)"
        sleep 5
    done
    
    log_error "$service_name 服务启动超时"
    return 1
}

# 创建Kafka主题
create_kafka_topics() {
    log_info "创建Kafka主题..."
    
    topics=(
        "adam-monitor-logs:3:1"
        "adam-trace-test-topic:3:1"
        "user-events:3:1"
        "order-events:3:1"
        "notification-events:3:1"
    )
    
    for topic_config in "${topics[@]}"; do
        IFS=':' read -r topic_name partitions replication <<< "$topic_config"
        
        docker exec adam-kafka kafka-topics \
            --create \
            --topic "$topic_name" \
            --partitions "$partitions" \
            --replication-factor "$replication" \
            --if-not-exists \
            --bootstrap-server localhost:9092
        
        log_info "创建主题: $topic_name (分区: $partitions, 副本: $replication)"
    done
    
    log_success "Kafka主题创建完成"
}

# 显示服务状态
show_service_status() {
    log_info "检查服务状态..."
    
    echo ""
    echo "=== 服务状态 ==="
    docker-compose -f docker-compose-test.yml ps
    
    echo ""
    echo "=== 服务访问地址 ==="
    echo "Nacos控制台:     http://localhost:8848/nacos (nacos/nacos)"
    echo "Kafka UI:        http://localhost:8080"
    echo "ClickHouse:      http://localhost:8123 (adam/adam123)"
    echo "Prometheus:      http://localhost:9090"
    echo "Grafana:         http://localhost:3000 (admin/admin123)"
    echo "Jaeger:          http://localhost:16686"
    echo "Redis:           localhost:6379 (密码: redis123)"
    echo ""
}

# 主函数
main() {
    log_info "开始启动Adam Monitor测试环境..."
    
    check_prerequisites
    create_directories
    create_nacos_init_sql
    create_clickhouse_init_sql
    start_infrastructure
    
    # 等待所有服务完全启动
    sleep 10
    
    create_kafka_topics
    show_service_status
    
    log_success "Adam Monitor测试环境启动完成！"
    log_info "您现在可以运行集成测试了"
}

# 脚本入口
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi 