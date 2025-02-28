package com.adam.time;

import org.apache.commons.net.ntp.TimeStamp;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;

public class OptimizedNTPClient {
    
    // 1. 缓冲区复用池（预分配固定大小的ByteBuffer）
    private static final int BUFFER_SIZE = 48;
    private static final BlockingQueue<ByteBuffer> bufferPool = 
        new ArrayBlockingQueue<>(100); // 池容量100

    static {
        // 预填充缓冲区
        for (int i = 0; i < 100; i++) {
            bufferPool.offer(ByteBuffer.allocateDirect(BUFFER_SIZE));
        }
    }

    // 2. Reactor线程组配置
    private static final ExecutorService bossExecutor = 
        Executors.newSingleThreadExecutor(); // 主Reactor线程
    private static final ExecutorService workerExecutor = 
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()); // 工作线程
    
    // 3. 全局Selector（非阻塞模式核心）
    private static Selector selector;
    
    public static void main(String[] args) throws Exception {
        initSelector();
        startReactor();
        sendRequests(2000); // 发送100次请求

    }

    // 初始化Selector并配置超时
    private static void initSelector() throws IOException {
        selector = Selector.open();
    }

    // 启动主Reactor事件循环
    private static void startReactor() {
        bossExecutor.execute(() -> {
            while (!Thread.interrupted()) {
                try {
                    // 带超时的select调用
                    if (selector.select(10) > 0) {
                        Set<SelectionKey> keys = selector.selectedKeys();
                        Iterator<SelectionKey> iter = keys.iterator();
                        while (iter.hasNext()) {
                            SelectionKey key = iter.next();
                            iter.remove();
                            if (key.isReadable()) {
                                // 异步处理读事件
                                workerExecutor.submit(() -> handleResponse(key));
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // 发送批量请求（非阻塞模式）
    private static void sendRequests(int count) throws Exception {
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.connect(new InetSocketAddress("pool.ntp.org", 123));
        
        // 注册读事件
        channel.register(selector, SelectionKey.OP_READ);
        
        // 使用池化缓冲区发送请求
        for (int i = 0; i < count; i++) {
            ByteBuffer buffer = bufferPool.take(); // 从池中获取
            buffer.clear();
            
            // 构建NTP请求包（参考RFC 5905）
            buffer.put((byte) 0x1B); // LI=0, Version=3, Mode=3 (Client)
            buffer.position(48); // 填充48字节标准包
            buffer.flip();
            
            // 异步发送
            channel.write(buffer);
            bufferPool.offer(buffer); // 归还缓冲区到池
        }
    }

    // 处理服务器响应（业务逻辑隔离）
    private static void handleResponse(SelectionKey key) {
        DatagramChannel channel = (DatagramChannel) key.channel();
        ByteBuffer buffer = bufferPool.poll();
        try {
            channel.read(buffer);
            buffer.flip();
            
            // 解析NTP响应（示例，实际需完整解析RFC格式）
            long seconds = buffer.getInt(32) & 0xFFFFFFFFL;
            long fraction = buffer.getInt(36) & 0xFFFFFFFFL;
            long ntpTime = seconds * 1000 + (fraction * 1000L) / 0x100000000L;
            
            // 转换为UTC时间
            TimeStamp timeStamp = new TimeStamp(ntpTime);
            System.out.println("Server time: " + timeStamp.toDateString());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            bufferPool.offer(buffer); // 确保缓冲区归还
        }
    }
}