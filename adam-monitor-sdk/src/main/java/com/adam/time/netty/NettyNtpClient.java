package com.adam.time.netty;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.apache.commons.net.ntp.TimeStamp;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class NettyNtpClient {
    private static final int NTP_PORT = 123;
    private static final int NTP_PACKET_SIZE = 48;
    private Channel channel;
    private static final ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
    private final Bootstrap bootstrap;
    private final EventLoopGroup workerGroup;
    private final ConcurrentHashMap<String, CompletableFuture<Long>> pendingRequests = new ConcurrentHashMap<>();

    public NettyNtpClient() throws InterruptedException {
        // 根据操作系统自动选择IO模型（macOS使用kqueue）
        if (KQueue.isAvailable()) {
            workerGroup = new KQueueEventLoopGroup(Runtime.getRuntime().availableProcessors(), new DefaultThreadFactory("ntp-worker"));
        } else {
            workerGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors(), new DefaultThreadFactory("ntp-worker"));
        }

        bootstrap = new Bootstrap()
                .group(workerGroup)
                .option(ChannelOption.ALLOCATOR, allocator) // 启用内存池
                .channel(KQueue.isAvailable() ? KQueueDatagramChannel.class : NioDatagramChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new NtpHandler(pendingRequests));
                    }
                });
        channel = bootstrap.bind(0).sync().channel(); // 初始化时绑定
    }

    public CompletableFuture<Long> getNtpTimeAsync(String host) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        try {
            InetSocketAddress serverAddress = new InetSocketAddress(host, NTP_PORT);
//            ByteBuf buffer = Unpooled.buffer(NTP_PACKET_SIZE);

            ByteBuf buffer = allocator.directBuffer(NTP_PACKET_SIZE);  // 创建后保留引用

            // 构造RFC 5905标准请求包
            buffer.writeByte(0x1B);  // LI=0, VN=3, Mode=3
            buffer.writeByte(0x01);  // Stratum=1（有效层级）
            buffer.writeByte(0x00);  // Poll Interval
            buffer.writeByte(0x00);  // Precision
            buffer.writeZero(20);    // Root Delay等字段
            buffer.writerIndex(40);  // Transmit Timestamp从40字节开始
            // 设置Transmit Timestamp（当前时间）
            long currentTime = System.currentTimeMillis();
            long ntpTime = (currentTime / 1000) + 2208988800L;
            long fraction = ((currentTime % 1000) * 0x100000000L) / 1000;
            buffer.writeInt((int) ntpTime);
            buffer.writeInt((int) fraction);
            buffer.writerIndex(48); // 明确结束位置


            // 生成唯一请求ID
            String requestId = String.format("%d-%d", ntpTime, fraction);
            pendingRequests.put(requestId, future);
//            System.out.println("Sent: " + ByteBufUtil.hexDump(buffer));
            // 异步发送请求
            // 发送并添加释放监听器
            channel.writeAndFlush(new DatagramPacket(buffer, serverAddress))
                    .addListener(f -> buffer.release()); // 发送后释放


            // 设置5秒超时
            workerGroup.schedule(() -> {
                if (pendingRequests.remove(requestId) != null) {
                    future.completeExceptionally(new Throwable("时间超时"));
                }
            }, 5, TimeUnit.SECONDS);

        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public void shutdown() {
        workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS);
    }

    private static class NtpHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        private final ConcurrentHashMap<String, CompletableFuture<Long>> pendingRequests;

        public NtpHandler(ConcurrentHashMap<String, CompletableFuture<Long>> pendingRequests) {
            this.pendingRequests = pendingRequests;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
            ByteBuf content = packet.content();
            if (content.readableBytes() < NTP_PACKET_SIZE) return;

            // 提取Originate Timestamp作为请求ID
            long originateSeconds = content.getUnsignedInt(24);  // 正确位置
            long originateFraction = content.getUnsignedInt(28);


            String responseId = String.format("%d-%d", originateSeconds, originateFraction);

            // 匹配并处理响应
            CompletableFuture<Long> future = pendingRequests.remove(responseId);
            if (future != null) {
                // 解析Transmit Timestamp
                long seconds = content.getUnsignedInt(32);
                long fraction = content.getUnsignedInt(36);
                long ntpTime = (seconds - 2208988800L) * 1000L +
                        (fraction * 1000L) / 0x100000000L;
                future.complete(ntpTime);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            pendingRequests.values().forEach(f -> f.completeExceptionally(cause));
            ctx.close();
        }
    }

    public static void main(String[] args) throws Exception {
        NettyNtpClient client = new NettyNtpClient();
        
        // 示例：连续调用三次
        for (int i = 0; i < 100; i++) {
            client.getNtpTimeAsync("pool.ntp.org")
                  .thenAccept(time -> 
                      System.out.println("NTP Time: " + new TimeStamp(time).toDateString())
                  )
                  .exceptionally(e -> {
                      System.err.println("Error: " + e.getMessage());
                      return null;
                  });
            Thread.sleep(1000);
        }
        
        // 优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown));
    }
}