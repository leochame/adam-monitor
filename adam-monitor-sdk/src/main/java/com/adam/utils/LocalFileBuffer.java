package com.adam.utils;

import com.adam.entitys.LogMessage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地文件缓冲区
 * 用于背压处理时的本地存储
 * 特性：
 * 1. 异步写入
 * 2. 文件轮转
 * 3. 自动清理
 * 4. 压缩存储
 */
public class LocalFileBuffer {
    
    private final String systemName;
    private final Path bufferDir;
    private final BlockingQueue<LogMessage> writeQueue;
    private final ExecutorService writeExecutor;
    private final ScheduledExecutorService cleanupExecutor;
    
    // 配置参数
    private final int maxFileSize = 10 * 1024 * 1024; // 10MB
    private final int maxFiles = 100;
    private final long maxRetentionHours = 24;
    private final int writeQueueCapacity = 10000;
    
    // 状态管理
    private final AtomicLong currentFileSize = new AtomicLong(0);
    private final AtomicLong totalWritten = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(true);
    
    // 当前写入文件
    private volatile BufferedWriter currentWriter;
    private volatile Path currentFile;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    public LocalFileBuffer(String systemName) {
        this.systemName = systemName;
        this.bufferDir = createBufferDirectory();
        this.writeQueue = new LinkedBlockingQueue<>(writeQueueCapacity);
        this.writeExecutor = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "LocalFileBuffer-Writer"));
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "LocalFileBuffer-Cleanup"));
        
        // 启动写入任务
        writeExecutor.submit(this::writeTask);
        
        // 启动清理任务（每小时执行一次）
        cleanupExecutor.scheduleAtFixedRate(this::cleanupOldFiles, 
            1, 1, TimeUnit.HOURS);
    }
    
    /**
     * 投递消息到本地缓冲区
     */
    public boolean offer(LogMessage message) {
        if (!running.get()) {
            return false;
        }
        
        boolean success = writeQueue.offer(message);
        if (!success) {
            totalDropped.incrementAndGet();
        }
        return success;
    }
    
    /**
     * 异步写入任务
     */
    private void writeTask() {
        while (running.get() || !writeQueue.isEmpty()) {
            try {
                LogMessage message = writeQueue.poll(1, TimeUnit.SECONDS);
                if (message != null) {
                    writeMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error writing to local buffer: " + e.getMessage());
            }
        }
        
        // 关闭当前写入器
        closeCurrentWriter();
    }
    
    /**
     * 写入单条消息
     */
    private void writeMessage(LogMessage message) throws IOException {
        ensureWriterAvailable();
        
        // 序列化消息
        String jsonLine = objectMapper.writeValueAsString(message) + "\n";
        byte[] data = jsonLine.getBytes("UTF-8");
        
        // 检查文件大小限制
        if (currentFileSize.get() + data.length > maxFileSize) {
            rotateFile();
            ensureWriterAvailable();
        }
        
        // 写入数据
        currentWriter.write(jsonLine);
        currentWriter.flush();
        
        currentFileSize.addAndGet(data.length);
        totalWritten.incrementAndGet();
    }
    
    /**
     * 确保写入器可用
     */
    private void ensureWriterAvailable() throws IOException {
        if (currentWriter == null) {
            createNewFile();
        }
    }
    
    /**
     * 创建新文件
     */
    private void createNewFile() throws IOException {
        String timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = String.format("%s_buffer_%s.log", systemName, timestamp);
        
        currentFile = bufferDir.resolve(filename);
        currentWriter = Files.newBufferedWriter(currentFile, 
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        currentFileSize.set(0);
    }
    
    /**
     * 轮转文件
     */
    private void rotateFile() {
        closeCurrentWriter();
        
        // 压缩旧文件（可选）
        if (currentFile != null) {
            compressFileAsync(currentFile);
        }
    }
    
    /**
     * 关闭当前写入器
     */
    private void closeCurrentWriter() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException e) {
                System.err.println("Error closing writer: " + e.getMessage());
            } finally {
                currentWriter = null;
                currentFile = null;
            }
        }
    }
    
    /**
     * 异步压缩文件
     */
    private void compressFileAsync(Path file) {
        CompletableFuture.runAsync(() -> {
            try {
                Path gzFile = Paths.get(file.toString() + ".gz");
                try (InputStream in = Files.newInputStream(file);
                     OutputStream out = Files.newOutputStream(gzFile);
                     java.util.zip.GZIPOutputStream gzOut = 
                         new java.util.zip.GZIPOutputStream(out)) {
                    
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        gzOut.write(buffer, 0, len);
                    }
                }
                
                // 删除原文件
                Files.deleteIfExists(file);
                
            } catch (IOException e) {
                System.err.println("Error compressing file " + file + ": " + e.getMessage());
            }
        });
    }
    
    /**
     * 清理过期文件
     */
    private void cleanupOldFiles() {
        try {
            long cutoffTime = System.currentTimeMillis() - 
                (maxRetentionHours * 60 * 60 * 1000);
            
            Files.list(bufferDir)
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        System.err.println("Error deleting old file " + path + ": " + e.getMessage());
                    }
                });
                
            // 检查文件数量限制
            limitFileCount();
            
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }
    
    /**
     * 限制文件数量
     */
    private void limitFileCount() throws IOException {
        Files.list(bufferDir)
            .sorted((p1, p2) -> {
                try {
                    return Files.getLastModifiedTime(p1)
                        .compareTo(Files.getLastModifiedTime(p2));
                } catch (IOException e) {
                    return 0;
                }
            })
            .skip(Math.max(0, maxFiles))
            .forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    System.err.println("Error deleting excess file " + path + ": " + e.getMessage());
                }
            });
    }
    
    /**
     * 创建缓冲区目录
     */
    private Path createBufferDirectory() {
        Path dir = Paths.get(System.getProperty("user.home"), 
            ".adam-monitor", "buffer", systemName);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create buffer directory: " + dir, e);
        }
        return dir;
    }
    
    /**
     * 获取统计信息
     */
    public BufferStats getStats() {
        return new BufferStats(
            totalWritten.get(),
            totalDropped.get(),
            writeQueue.size(),
            currentFileSize.get()
        );
    }
    
    /**
     * 关闭缓冲区
     */
    public void close() {
        running.set(false);
        
        try {
            // 等待写入任务完成
            writeExecutor.shutdown();
            if (!writeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                writeExecutor.shutdownNow();
            }
            
            // 关闭清理任务
            cleanupExecutor.shutdown();
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 缓冲区统计信息
     */
    public static class BufferStats {
        public final long totalWritten;
        public final long totalDropped;
        public final int queueSize;
        public final long currentFileSize;
        
        public BufferStats(long totalWritten, long totalDropped, 
                          int queueSize, long currentFileSize) {
            this.totalWritten = totalWritten;
            this.totalDropped = totalDropped;
            this.queueSize = queueSize;
            this.currentFileSize = currentFileSize;
        }
        
        public double getDropRate() {
            long total = totalWritten + totalDropped;
            return total > 0 ? (double) totalDropped / total : 0.0;
        }
    }
}