package com.adam.listener;

import com.adam.service.LogAnalyticalService;
import org.redisson.api.listener.MessageListener;
import com.adam.listener.LogMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Component
public class MonitorLogListener implements MessageListener<LogMessage> {

    private BlockingQueue<LogMessage> logQueue = new LinkedBlockingQueue<>();
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean isScheduled = false;

    // 批量大小和时间窗口配置
    private static final int BATCH_SIZE = 100;
    private static final int TIME_WINDOW_SECONDS = 5;

    @PostConstruct
    public void init() {
        // 启动定时任务，固定时间间隔检查
        scheduler.scheduleAtFixedRate(this::processBatch, TIME_WINDOW_SECONDS, TIME_WINDOW_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void onMessage(CharSequence charSequence, LogMessage logMessage) {
        logQueue.offer(logMessage); // 非阻塞添加
        // 达到批量大小时立即触发处理
        if (logQueue.size() >= BATCH_SIZE) {
            processBatch();
        }
    }

    private void processBatch() {
        List<LogMessage> batch = new ArrayList<>(BATCH_SIZE);
        logQueue.drainTo(batch, BATCH_SIZE); // 批量取出
        if (!batch.isEmpty()) {
            try {
                // 调用批量插入方法
                batchInsertLogs(batch);
            } catch (Exception e) {
                // 异常处理（例如：重试或记录失败）
                handleInsertError(batch, e);
            }
        }
    }

    private void batchInsertLogs(List<LogMessage> logs) {
        // 实现批量插入逻辑，例如使用JdbcTemplate或MyBatis批量操作
        // 示例：jdbcTemplate.batchUpdate(...);
    }

    private void handleInsertError(List<LogMessage> failedBatch, Exception e) {
        // 处理插入失败的日志，如重新放回队列或记录到文件
        logQueue.addAll(failedBatch);
        // 日志记录异常
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown(); // 停止定时任务
        // 处理剩余日志
        processBatch();
    }
}
