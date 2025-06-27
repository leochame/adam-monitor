# Adam Monitor SDK - 业务上下文传播模块

## 功能介绍

该模块基于SkyWalking实现了业务上下文的自动传播功能，支持：

1. 跨RPC调用的业务上下文传播（支持Dubbo和Spring Cloud Feign等）
2. 跨消息队列的业务上下文传播（支持RocketMQ和Kafka等）
3. 在Web请求中自动提取和传播业务上下文

## 特性

- 兼容不同版本的Spring Boot（2.x和3.x）
- 兼容不同版本的JDK（1.8及以上）
- 基于SkyWalking的上下文传播机制，无侵入式集成
- 提供降级方案，在SkyWalking不可用时使用本地线程上下文
- 自动配置，开箱即用

## 使用方法

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.adam</groupId>
    <artifactId>adam-monitor-sdk</artifactId>
    <version>${adam.version}</version>
</dependency>
```

### 2. 配置SkyWalking Agent

为了充分利用SkyWalking的上下文传播能力，建议配置SkyWalking Agent。在应用启动时添加以下JVM参数：

```
-javaagent:/path/to/skywalking-agent.jar
-Dskywalking.agent.service_name=your-service-name
-Dskywalking.collector.backend_service=skywalking-oap:11800
```

### 3. 在Web应用中使用

在Spring Boot应用中，SDK会自动注册Web拦截器，从请求头中提取业务ID（AID）并设置到当前线程的上下文中。

客户端发送请求时，可以在请求头中添加`X-Adam-AID`字段，服务端会自动提取并设置到上下文中。

```java
HttpHeaders headers = new HttpHeaders();
headers.set("X-Adam-AID", "your-business-id");
// 发送请求...
```

服务端处理请求时，可以通过`TraceContext`获取业务ID：

```java
@RestController
public class YourController {
    
    @GetMapping("/your-api")
    public String yourApi() {
        // 获取业务ID
        String aid = AdamTraceContext.getAid();
        // 业务处理...
        return "success";
    }
}
```

### 4. 在RPC调用中使用

#### Dubbo示例

```java
// 使用统一的AdamTraceContext，无需依赖注入

// 在Dubbo Filter中使用
public class YourDubboFilter implements Filter {
    
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 客户端：调用前注入上下文
        if (RpcContext.getContext().isConsumerSide()) {
            propagator.injectContext(invocation.getAttachments());
        }
        
        // 服务端：调用前提取上下文
        if (RpcContext.getContext().isProviderSide()) {
            propagator.extractContext(invocation.getAttachments());
        }
        
        try {
            return invoker.invoke(invocation);
        } finally {
            // 服务端：调用后清理上下文
            if (RpcContext.getContext().isProviderSide()) {
                propagator.cleanContext();
            }
        }
    }
}
```

#### Spring Cloud Feign示例

```java
// 获取TraceContextProcessor
@Autowired
private TraceContextProcessor traceContextProcessor;

// 创建RPC上下文传播器
RpcContextPropagator propagator = new RpcContextPropagator(traceContextProcessor);

// 在Feign RequestInterceptor中使用
@Component
public class YourFeignInterceptor implements RequestInterceptor {
    
    @Override
    public void apply(RequestTemplate template) {
        // 调用前注入上下文
        Map<String, String> contextMap = propagator.injectContext();
        contextMap.forEach(template::header);
    }
}

// 在Controller中提取上下文
@RestController
public class YourController {
    
    @GetMapping("/your-api")
    public String yourApi(HttpServletRequest request) {
        // 从请求头中提取业务ID
        String aid = request.getHeader("X-Adam-AID");
        if (aid != null && !aid.isEmpty()) {
            AdamTraceContext.setAid(aid);
        }
        
        try {
            // 业务处理...
            return "success";
        } finally {
            // 清理上下文
            AdamTraceContext.clearAid();
        }
    }
}
```

### 5. 在消息队列中使用

#### RocketMQ示例

```java
// 使用统一的AdamTraceContext，无需依赖注入

// 生产者：发送消息前注入上下文
public void sendMessage(String topic, String tag, String content) {
    Message message = new Message(topic, tag, content.getBytes());
    
    // 注入上下文
    Map<String, String> properties = new HashMap<>();
    propagator.injectContext(properties);
    properties.forEach((key, value) -> message.putUserProperty(key, value));
    
    // 发送消息...
    producer.send(message);
}

// 消费者：消费消息前提取上下文
public class YourMessageListener implements MessageListenerConcurrently {
    
    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                // 提取上下文
                Map<String, String> properties = new HashMap<>();
                msg.getProperties().forEach(properties::put);
                propagator.extractContext(properties);
                
                // 处理消息...
                
            } finally {
                // 清理上下文
                propagator.cleanContext();
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
```

#### Kafka示例

```java
// 使用统一的AdamTraceContext，无需依赖注入

// 生产者：发送消息前注入上下文
public void sendMessage(String topic, String content) {
    ProducerRecord<String, String> record = new ProducerRecord<>(topic, content);
    
    // 注入上下文
    Map<String, String> headers = new HashMap<>();
    propagator.injectContext(headers);
    headers.forEach((key, value) -> record.headers().add(key, value.getBytes()));
    
    // 发送消息...
    producer.send(record);
}

// 消费者：消费消息前提取上下文
@KafkaListener(topics = "your-topic")
public void consumeMessage(ConsumerRecord<String, String> record) {
    try {
        // 提取上下文
        Map<String, String> headers = new HashMap<>();
        record.headers().forEach(header -> {
            headers.put(header.key(), new String(header.value()));
        });
        propagator.extractContext(headers);
        
        // 处理消息...
        
    } finally {
        // 清理上下文
        propagator.cleanContext();
    }
}
```

## 配置选项

在`application.properties`或`application.yml`中可以进行以下配置：

```properties
# 启用或禁用业务上下文传播功能，默认为true
adam.trace.enabled=true
```

## 注意事项

1. 虽然SDK提供了在SkyWalking不可用时的降级方案，但为了获得最佳的跨服务传播效果，建议配置SkyWalking Agent。
2. 在使用异步线程或线程池时，需要手动传递业务上下文，可以使用以下方式：

```java
// 获取当前线程的业务ID
String aid = AdamTraceContext.getAid();

// 在新线程中设置业务ID
executor.submit(() -> {
    try {
        AdamTraceContext.setAid(aid);
        // 业务处理...
    } finally {
        AdamTraceContext.clearAid();
    }
});
```

3. 在使用Spring的`@Async`注解时，可以通过实现`AsyncConfigurer`来自动传递业务上下文：

```java
@Configuration
public class AsyncConfig implements AsyncConfigurer {
    
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 配置线程池...
        executor.setTaskDecorator(runnable -> {
            String aid = AdamTraceContext.getAid();
            return () -> {
                try {
                    if (aid != null) {
                        AdamTraceContext.setAid(aid);
                    }
                    runnable.run();
                } finally {
                    AdamTraceContext.clearAid();
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
```