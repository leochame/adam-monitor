package com.adam.ntp.handler;

import com.adam.ntp.core.ClockCompensator;
import com.adam.ntp.routing.NtpRouter;

import java.io.IOException;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.*;

public class NioNetworkHandler {
    private static final int BUFFER_SIZE = 48;
    private final BlockingQueue<ByteBuffer> bufferPool = new ArrayBlockingQueue<>(100);
    private final ExecutorService bossExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService workerExecutor;
    private Selector selector;
    private final NtpRouter router;
    private final ClockCompensator compensator;

    public NioNetworkHandler(NtpRouter router, ClockCompensator compensator) throws IOException {
        this.router = router;
        this.compensator = compensator;
        this.workerExecutor = Executors.newWorkStealingPool();
        initialize();
    }

    private void initialize() throws IOException {
        for (int i = 0; i < 100; i++) {
            bufferPool.offer(ByteBuffer.allocateDirect(BUFFER_SIZE));
        }
        selector = Selector.open();
        bossExecutor.execute(this::eventLoop);
    }

    private void eventLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (selector.select(10) > 0) {
                    processSelectedKeys();
                }
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private void processSelectedKeys() {
        Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
        while (iter.hasNext()) {
            SelectionKey key = iter.next();
            iter.remove();
            if (key.isReadable()) {
                workerExecutor.execute(() -> {
                    try {
                        handleRead(key);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        DatagramChannel channel = (DatagramChannel) key.channel();
        ByteBuffer buffer = bufferPool.poll();
        InetSocketAddress address = (InetSocketAddress) channel.getRemoteAddress();
        try {

            channel.read(buffer);
            buffer.flip();
            
            long ntpTime = parseTimestamp(buffer);
            long rtt = calculateRtt(buffer);
            long adjusted = compensator.compensate(ntpTime, rtt);
            
            System.out.println("Adjusted Time: " + new Date(adjusted));
        } catch (Exception e) {
//            router.handleFailure(address.getHostName());
        } finally {
            bufferPool.offer(buffer);
        }
    }

    private long calculateRtt(ByteBuffer buffer) {
        // 解析NTP包中的四个关键时间戳（RFC 5905定义）
        long originateTimestamp = getTimestamp(buffer, 24); // 客户端发送时间（t1）
        long receiveTimestamp   = getTimestamp(buffer, 32); // 服务器接收时间（t2）
        long transmitTimestamp  = getTimestamp(buffer, 40); // 服务器发送时间（t3）
        long destinationTimestamp = System.currentTimeMillis(); // 客户端接收时间（t4）

        // 计算延迟公式：delay = (t4 - t1) - (t3 - t2)
        long delay = (destinationTimestamp - originateTimestamp) -
                (transmitTimestamp - receiveTimestamp);

        return Math.max(delay, 0); // 确保非负
    }

    // 从缓冲区解析NTP时间戳（网页5的时序数据处理思路）
    private long getTimestamp(ByteBuffer buffer, int position) {
        long seconds = buffer.getInt(position) & 0xFFFFFFFFL;
        long fraction = buffer.getInt(position + 4) & 0xFFFFFFFFL;
        return (seconds * 1000) + ((fraction * 1000L) / 0x100000000L);
    }

    public void sendRequest(String host) throws IOException, InterruptedException {
        InetSocketAddress address = new InetSocketAddress(host, 123);
        DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        channel.connect(address);
        channel.register(selector, SelectionKey.OP_READ);

        ByteBuffer buffer = bufferPool.take();
        try {
            prepareRequest(buffer);
            channel.write(buffer);
        } finally {
            bufferPool.offer(buffer);
        }
    }

    private void prepareRequest(ByteBuffer buffer) {
        buffer.clear();
        buffer.put((byte) 0x23); // NTPv4 client
        buffer.position(48);
        buffer.flip();
    }

    private long parseTimestamp(ByteBuffer buffer) {
        long seconds = buffer.getInt(32) & 0xFFFFFFFFL;
        long fraction = buffer.getInt(36) & 0xFFFFFFFFL;
        return (seconds * 1000) + ((fraction * 1000L) >>> 32);
    }

    private void handleError(Throwable e) {
        // Error handling logic
    }

    public void shutdown() {
        bossExecutor.shutdown();
        workerExecutor.shutdown();
        try {
            selector.close();
        } catch (IOException e) {
            // Close exception handling
        }
    }
}