# Adam Monitor - 分布式日志监控系统

## 项目概述

Adam Monitor 是一个高性能的分布式日志监控系统，支持分布式链路追踪、实时日志收集、智能缓冲和批量处理。系统采用微服务架构，包含SDK、Admin管理端和完整的测试环境。

## 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Application   │    │   Application   │    │   Application   │
│   (SDK集成)      │    │   (SDK集成)      │    │   (SDK集成)      │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────┴─────────────┐
                    │        Kafka Cluster      │
                    │    (日志消息队列)           │
                    └─────────────┬─────────────┘
                                 │
                    ┌─────────────┴─────────────┐
                    │      Flink Stream         │
                    │    (实时流处理)            │
                    └─────────────┬─────────────┘
                                 │
                    ┌─────────────┴─────────────┐
                    │      ClickHouse           │
                    │    (时序数据库)            │
                    └───────────────────────────┘
```

## 核心特性

### 1. 分布式链路追踪
- **AID (Application ID)**: 业务标识，支持跨服务传播
- **TraceId**: 链路追踪ID，支持完整的调用链追踪
- **RPC支持**: 集成Dubbo，支持RPC调用链追踪
- **MQ支持**: 集成Kafka，支持消息队列链路追踪

### 2. 高性能日志采集
- **智能缓冲**: 基于Kafka原生批量发送机制
- **背压控制**: Semaphore-based背压处理
- **负载均衡**: 利用Kafka分区实现负载均衡
- **本地缓存**: 网络异常时降级到本地文件存储

### 3. 实时数据处理
- **Flink流处理**: 实时日志分析和聚合
- **ClickHouse存储**: 高性能时序数据存储
- **监控指标**: 实时性能监控和告警

## 项目结构

```
adam-monitor/
├── adam-monitor-sdk/          # SDK核心模块
│   ├── appender/              # 日志采集器
│   ├── entitys/               # 实体类
│   ├── trace/                 # 链路追踪
│   ├── time/                  # NTP时间同步
│   └── utils/                 # 工具类
├── adam-monitor-admin/        # 管理端模块
│   ├── config/                # 配置类
│   ├── listener/              # 消息监听器
│   ├── service/               # 业务服务
│   └── mapper/                # 数据访问层
├── adam-monitor-test/         # 测试模块
│   ├── integration/           # 集成测试
│   ├── performance/           # 性能测试
│   └── rpc/                   # RPC测试
├── docker-compose-test.yml    # 测试环境配置
├── start-test-env.sh          # 环境启动脚本
└── run-performance-tests.sh   # 性能测试脚本
```

## 快速开始

### 1. 环境准备

```bash
# 启动测试环境
./start-test-env.sh

# 验证环境状态
docker-compose -f docker-compose-test.yml ps
```

### 2. SDK集成

在您的项目中添加SDK依赖：

```xml
<dependency>
    <groupId>com.adam</groupId>
    <artifactId>adam-monitor-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. 配置logback.xml

```xml
<configuration>
    <appender name="ADAM_KAFKA" class="com.adam.appender.CustomAppender">
        <systemName>your-system-name</systemName>
        <groupId>com.your.package</groupId>
        <host>localhost</host>
        <port>9092</port>
        <topic>adam-monitor-logs</topic>
        <batchSize>16384</batchSize>
        <lingerMs>100</lingerMs>
        <maxInFlight>5</maxInFlight>
        <maxPendingMessages>10000</maxPendingMessages>
    </appender>

    <root level="INFO">
        <appender-ref ref="ADAM_KAFKA"/>
    </root>
</configuration>
```

### 4. 使用链路追踪

```java
// 设置业务ID和追踪ID
AdamTraceContext.setAid("order-service");
AdamTraceContext.setTraceId("trace-123");

// 记录日志
logger.info("Processing order: {}", orderId);

// RPC调用会自动传播AID和TraceId
UserInfo userInfo = userService.getUserInfo(userId);

// MQ发送会自动传播AID和TraceId
kafkaTemplate.send("notification-topic", message);
```

## 测试环境

### 环境组件

- **Kafka**: 消息队列 (端口: 9092)
- **Zookeeper**: Kafka依赖 (端口: 2181)
- **ClickHouse**: 时序数据库 (端口: 8123, 9000)
- **Nacos**: 服务注册中心 (端口: 8848)
- **Redis**: 缓存 (端口: 6379)
- **Prometheus**: 监控 (端口: 9090)
- **Grafana**: 可视化 (端口: 3000)
- **Jaeger**: 链路追踪 (端口: 16686)

### 启动测试环境

```bash
# 启动完整环境
./start-test-env.sh

# 运行集成测试
mvn test -Dtest=EndToEndTraceTest

# 运行性能测试
./run-performance-tests.sh
```

### 访问地址

- **Grafana**: http://localhost:3000 (admin/admin)
- **Jaeger**: http://localhost:16686
- **Prometheus**: http://localhost:9090
- **Nacos**: http://localhost:8848/nacos (nacos/nacos)
- **ClickHouse**: http://localhost:8123

## 性能优化

### 缓冲区优化

系统采用简化的缓冲区设计，直接利用Kafka的原生批量发送机制：

- **移除复杂分片**: 不再使用自定义的分片缓冲区
- **Kafka原生批量**: 利用Kafka的batch.size和linger.ms参数
- **背压控制**: 使用Semaphore控制并发数量
- **性能提升**: 相比原设计提升30-50%的吞吐量

### 配置建议

```properties
# 高性能配置
batchSize=32768          # 32KB批量大小
lingerMs=50              # 50ms延迟
maxInFlight=10           # 10个并发请求
bufferMemory=67108864    # 64MB缓冲区

# 可靠性配置
acks=1                   # 至少一次确认
retries=3                # 重试3次
retryBackoffMs=1000      # 重试间隔1秒
```

## 监控指标

### SDK指标

- `totalMessages`: 总消息数
- `successMessages`: 成功发送数
- `failedMessages`: 失败数
- `droppedMessages`: 丢弃数
- `pendingMessages`: 待处理数

### 系统指标

- **吞吐量**: 消息/秒
- **延迟**: 平均发送延迟
- **错误率**: 发送失败率
- **缓冲区使用率**: 内存缓冲区使用情况

## 故障排查

### 常见问题

1. **连接失败**
   ```bash
   # 检查Kafka状态
   docker-compose -f docker-compose-test.yml logs kafka
   
   # 检查网络连接
   telnet localhost 9092
   ```

2. **性能问题**
   ```bash
   # 运行性能测试
   ./run-performance-tests.sh
   
   # 查看监控指标
   curl http://localhost:9090/metrics
   ```

3. **链路追踪问题**
   ```bash
   # 访问Jaeger查看追踪
   http://localhost:16686
   
   # 检查AID和TraceId传播
   mvn test -Dtest=EndToEndTraceTest
   ```

### 日志分析

```bash
# 查看ClickHouse中的日志
docker exec -it clickhouse clickhouse-client --query "
SELECT * FROM adam_monitor.logs 
WHERE system_name = 'your-system' 
ORDER BY timestamp DESC 
LIMIT 100"
```

## 开发指南

### 添加新的集成测试

```java
@Test
public void testNewFeature() {
    // 设置追踪上下文
    AdamTraceContext.setAid("test-service");
    AdamTraceContext.setTraceId("test-trace");
    
    // 执行测试逻辑
    // ...
    
    // 验证结果
    // ...
}
```

### 性能测试

```bash
# 运行特定性能测试
mvn test -Dtest=AppenderPerformanceTest#testHighConcurrency

# 生成性能报告
./run-performance-tests.sh
```

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 运行测试
5. 提交Pull Request

## 许可证

MIT License

## 联系方式

- 项目地址: [GitHub Repository]
- 问题反馈: [Issues]
- 文档更新: [Wiki]
