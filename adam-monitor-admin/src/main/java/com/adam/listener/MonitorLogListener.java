package com.adam.listener;

import com.adam.service.LogAnalyticalService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class MonitorLogListener {

    private BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isScheduled = false;
    private final AtomicInteger processedCount = new AtomicInteger(0);
    
    @Autowired
    private LogAnalyticalService logAnalyticalService;

    // 批量大小和时间窗口配置
    private static final int BATCH_SIZE = 100;
    private static final int TIME_WINDOW_SECONDS = 5;

    @PostConstruct
    public void init() {
        // 启动定时任务，固定时间间隔检查
        scheduler.scheduleAtFixedRate(this::processBatch, TIME_WINDOW_SECONDS, TIME_WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 添加日志消息到处理队列
     * @param logMessage 日志消息JSON字符串
     */
    public void addLogMessage(String logMessage) {
        logQueue.offer(logMessage); // 非阻塞添加
        // 达到批量大小时立即触发处理
        if (logQueue.size() >= BATCH_SIZE) {
            processBatch();
        }
    }

    private void processBatch() {
        List<String> batch = new ArrayList<>(BATCH_SIZE);
        logQueue.drainTo(batch, BATCH_SIZE); // 批量取出
        if (!batch.isEmpty()) {
            try {
                // 调用批量插入方法
                logAnalyticalService.saveAll(batch);
                int count = processedCount.addAndGet(batch.size());
                log.info("已处理 {} 条日志消息", count);
            } catch (Exception e) {
                // 异常处理（例如：重试或记录失败）
                handleInsertError(batch, e);
                log.error("处理日志批次失败", e);
            }
        }
    }

    private void handleInsertError(List<String> failedBatch, Exception e) {
        // 处理插入失败的日志，如重新放回队列或记录到文件
        logQueue.addAll(failedBatch);
        log.error("批量插入日志失败，已重新加入队列，失败原因: {}", e.getMessage());
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown(); // 停止定时任务
        // 处理剩余日志
        processBatch();
    }
}
