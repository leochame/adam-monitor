# Adam 全链路监控系统

```` xml
<appender name="CUSTOM" class="com.adam.appender.CustomAppender">
    <systemName>xxxx</systemName>
    <groupId>com.adam</groupId>
    <host>xxxx</host>
    <port>xxxx</port>
    <pushType>redis<pushType>
</appender>
````
````sql
CREATE TABLE log_message (
                             id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键ID',
                             trace_id VARCHAR(255) COMMENT '分布式追踪ID（如UUID）',
                             system_name VARCHAR(255) COMMENT '产生日志的系统名称',
                             class_name VARCHAR(255) COMMENT '日志产生的类名',
                             method_name VARCHAR(255) COMMENT '日志产生的方法名',
                             log_content VARCHAR(1500) COMMENT '日志内容（可容纳500个中文汉字）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统日志存储表';

````

# NTP请求设置推荐
#### 多级缓存组合
- JVM缓存（300秒）
- 操作系统级缓存（通过nscd服务设置60秒TTL）
- 本地hosts文件应急备案

````bash
# Linux系统启用nscd服务
sudo systemctl start nscd
sudo echo "enable-cache hosts yes" >> /etc/nscd.conf
````

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


````yaml
# 在日志系统配置中增加：
async:
  ntp:
    core-pool-size: 2
    max-pool-size: 4
    queue-capacity: 1000
````
## 扩展建议：
1. 添加SNTP加密支持（RFC 7822）
2. 实现PTP协议混合模式
3. 集成Prometheus监控指标
4. 添加配置热加载功能