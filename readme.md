# Adam 全链路监控系统

## 系统架构

Adam全链路监控系统是一个分布式日志收集和分析平台，用于实时监控和分析分布式系统中的日志数据。系统采用了现代化的流处理架构，包含以下核心组件：

- **日志收集器**：通过自定义Appender收集应用日志
- **消息队列**：使用Kafka进行日志数据的缓冲和传输
- **流处理引擎**：使用Flink进行实时日志分析和处理
- **数据存储**：使用ClickHouse进行高性能日志存储和查询

### 架构流程图

```
+----------------+     +----------------+     +----------------+     +----------------+
|                |     |                |     |                |     |                |
|  应用服务      | --> |  Kafka         | --> |  Flink         | --> |  ClickHouse    |
|  CustomAppender|     |  消息队列      |     |  流处理引擎    |     |  列式存储      |
|                |     |                |     |                |     |                |
+----------------+     +----------------+     +----------------+     +----------------+
       |                                                                    ^
       |                                                                    |
       v                                                                    |
+----------------+                                               +----------------+
|                |                                               |                |
|  监控告警      | <-------------------------------------------- |  查询分析      |
|  系统          |                                               |  服务          |
|                |                                               |                |
+----------------+                                               +----------------+
```

### 数据流向详解

1. **日志采集层**：
   - 应用通过集成CustomAppender组件采集日志
   - 支持多线程异步处理，避免阻塞业务线程
   - 实现分片缓冲区(ShardedBuffer)和批量发送机制，提高吞吐量
   - 内置背压控制和降级策略，保障系统稳定性

2. **数据传输层**：
   - Kafka作为高吞吐量的消息队列，缓冲日志数据
   - 采用LZ4压缩算法减少网络传输开销
   - 基于系统名称的分区策略，确保同一系统日志顺序处理
   - 支持水平扩展，满足大规模日志收集需求

3. **数据处理层**：
   - Flink实时流处理引擎处理日志流
   - 基于时间窗口的批量处理策略，平衡实时性和处理效率
   - 支持状态管理和检查点机制，保障数据处理的可靠性
   - 可扩展的处理模型，支持复杂的日志分析场景

4. **数据存储层**：
   - ClickHouse高性能列式数据库存储日志数据
   - 针对日志查询场景优化的表结构和索引设计
   - 支持高效的聚合查询和时序分析
   - 数据分片和复制机制，保障数据的可靠性和查询性能

## 快速开始

### 环境要求

- Docker 和 Docker Compose
- 至少4GB可用内存
- 10GB可用磁盘空间

### 启动服务

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps
```

### 验证服务

1. **ClickHouse**：访问 http://localhost:8123 验证ClickHouse服务
2. **Kafka**：使用以下命令验证Kafka服务
   ```bash
   docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list
   ```
3. **Flink**：访问 http://localhost:8081 验证Flink Dashboard

## 中间件环境

系统使用Docker Compose编排以下中间件服务：

### ClickHouse

用于高性能日志存储和分析的列式数据库。

- **端口**：8123(HTTP), 9000(Native)
- **用户名**：admin
- **密码**：secure_password_123
- **表结构**：
  ```sql
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
  ```
- **索引优化**：
  ```sql
  ALTER TABLE log_table ADD INDEX idx_trace_id (trace_id) TYPE minmax GRANULARITY 3;
  ALTER TABLE log_table ADD INDEX idx_system_name (system_name) TYPE minmax GRANULARITY 3;
  ```

### Kafka & ZooKeeper

Kafka用于日志数据的传输和缓冲，ZooKeeper用于Kafka集群管理。

- **Kafka端口**：9092
- **ZooKeeper端口**：2181
- **默认主题**：adam-logs
- **配置优化**：
  - 压缩类型：LZ4
  - 批处理大小：32KB
  - 等待时间：50ms
  - 缓冲区大小：32MB

### Flink

用于实时流处理和日志分析的分布式计算引擎。

- **Dashboard端口**：8081
- **JobManager**：负责作业调度和资源分配
- **TaskManager**：负责执行具体的任务
- **处理策略**：
  - 时间窗口：3秒
  - 批处理大小：100条日志
  - 检查点存储：文件系统

## 应用配置

### 日志收集配置

在应用中添加以下Appender配置：

```xml
<appender name="CUSTOM" class="com.adam.appender.CustomAppender">
    <systemName>xxxx</systemName>
    <groupId>com.adam</groupId>
    <host>xxxx</host>
    <port>xxxx</port>
    <pushType>kafka</pushType>
    <queueCapacity>10000</queueCapacity>
    <batchSize>5</batchSize>
</appender>
```

### 性能优化配置

```xml
<!-- 异步处理配置 -->
<appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <appender-ref ref="CUSTOM" />
    <queueSize>512</queueSize>
    <discardingThreshold>0</discardingThreshold>
    <includeCallerData>true</includeCallerData>
</appender>
```

## 部署最佳实践

### 容器资源配置

- **ClickHouse**：建议至少2GB内存，4核CPU
- **Kafka**：建议至少2GB内存，2核CPU
- **Flink JobManager**：建议至少1GB内存，2核CPU
- **Flink TaskManager**：建议至少2GB内存，4核CPU

### 数据持久化

系统默认配置了以下数据卷，确保数据持久化：

- ClickHouse数据：`./ch_data:/var/lib/clickhouse/`
- ClickHouse日志：`./ch_logs:/var/log/clickhouse-server/`
- Kafka数据：`./kafka_data:/var/lib/kafka/data`
- ZooKeeper数据：`./zk_data:/var/lib/zookeeper/data`
- ZooKeeper日志：`./zk_log:/var/lib/zookeeper/log`
- Flink检查点：`./flink_checkpoints:/opt/flink/checkpoints`

### 高可用配置

对于生产环境，建议进行以下高可用配置：

1. **Kafka集群**：
   - 配置多个Broker节点
   - 主题复制因子设置为3
   - 启用自动创建主题

2. **ClickHouse集群**：
   - 配置多个副本
   - 启用分片和复制
   - 配置ZooKeeper集群管理

3. **Flink集群**：
   - 配置多个JobManager实例
   - 增加TaskManager节点数量
   - 启用高可用模式，使用ZooKeeper存储元数据

## 监控与运维

### 系统监控

建议集成以下监控工具：

1. **Prometheus + Grafana**：
   - 监控各组件资源使用情况
   - 监控日志处理延迟和吞吐量
   - 设置告警规则

2. **ELK Stack**：
   - 收集系统组件日志
   - 分析系统运行状态
   - 可视化展示系统健康状况

### 常见问题排查

1. **日志丢失问题**：
   - 检查CustomAppender配置
   - 验证Kafka连接状态
   - 查看Flink作业运行状态

2. **性能瓶颈分析**：
   - 监控Kafka消息积压情况
   - 检查ClickHouse查询性能
   - 分析Flink处理延迟

# NTP请求设置推荐
#### 多级缓存组合
- JVM缓存（300秒）
- 操作系统级缓存（通过nscd服务设置60秒TTL）
- 本地hosts文件应急备案

```bash
# Linux系统启用nscd服务
sudo systemctl start nscd
sudo echo "enable-cache hosts yes" >> /etc/nscd.conf
```

#### 多源分层架构
- 部署三级时钟源（阿里云stratum1节点 + 自建GPS原子钟 + 公共NTP池兜底）
- 实现RFC 6350规范的时钟源选举算法，动态选择最优节点

#### 智能路由熔断
- 基于服务质量评分（RTT/Stratum/Offset）动态路由
- 异常熔断机制：5分钟内3次超时自动隔离故障节点

#### 本地时钟驯服
- 采用卡尔曼滤波算法补偿网络抖动
- 双时钟源交叉校验，偏差>15ms触发强制同步

#### DNS高可用
- 实现TTL感知的DNS缓存（12小时刷新周期）
- 多地域解析fallback策略（阿里DNS + 114DNS + Google DNS）

## 如何不侵占业务线程
使用独立线程池处理NTP请求，与日志发送线程池隔离,通过队列容量限制最大积压请求

```yaml
# 在日志系统配置中增加：
async:
  ntp:
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 1000
```

## 扩展建议：
1. 添加SNTP加密支持（RFC 7822）
2. 实现PTP协议混合模式
3. 集成Prometheus监控指标
4. 添加配置热加载功能