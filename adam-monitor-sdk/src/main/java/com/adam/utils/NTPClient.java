package com.adam.utils;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.NtpV3Packet;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

// 自定义NTP服务器描述类
class NtpServer {


    private final String host;
    private final int stratum;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile long lastFailureTime;
    private volatile boolean active = true;

    public NtpServer(String host, int stratum) {
        this.host = host;
        this.stratum = stratum;
    }

    // 熔断状态检查（网页8）
    public boolean isAvailable() {
        return active && (failureCount.get() < 3) && 
               (System.currentTimeMillis() - lastFailureTime > 300_000);
    }

    public void recordFailure() {
        failureCount.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
        if(failureCount.get() >= 3) {
            active = false;
            Executors.newSingleThreadScheduledExecutor()
                .schedule(() -> active = true, 5, TimeUnit.MINUTES); // 熔断恢复（网页9）
        }
    }
    public int getStratum() {
        return stratum;
    }

    public String getHost() {
        return host;
    }
    // Getters...
}

// 自定义异常体系
class NtpException extends RuntimeException {
    public NtpException(String message, Throwable cause) {
        super(message, cause);
    }
}

class ServerUnreachableException extends NtpException {
    public ServerUnreachableException(String host) {
        super("NTP server unreachable: " + host, null);
    }
}

class StratumViolationException extends NtpException {
    public StratumViolationException(int stratum) {
        super("Invalid stratum level: " + stratum, null);
    }
}

// 时钟补偿计算器
class ClockCompensator {
    private static final int MAX_SAMPLES = 8;
    private final Deque<Long> offsetHistory = new ArrayDeque<>();
    private long lastCompensatedTime;

    public synchronized long compensate(long ntpTime, long rtt) {
        long localTime = System.currentTimeMillis();
        long offset = ntpTime - localTime - (rtt/2);
        
        // 滑动窗口滤波
        if(offsetHistory.size() >= MAX_SAMPLES) {
            offsetHistory.removeFirst();
        }
        offsetHistory.addLast(offset);
        
        long avgOffset = (long)offsetHistory.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
        
        // 15ms误差控制（需求约束）
        if(Math.abs(avgOffset) > 15) {
            lastCompensatedTime = localTime + avgOffset;
        }
        return lastCompensatedTime;
    }
}

// 智能路由选择器
class NtpRouter {
    private final List<NtpServer> servers;
    private final Map<String, InetAddress> dnsCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService dnsRefresher = 
        Executors.newSingleThreadScheduledExecutor();

    public NtpRouter(List<NtpServer> servers) {
        this.servers = servers;
        // DNS缓存自动刷新（网页2）
        dnsRefresher.scheduleAtFixedRate(this::refreshDns, 12, 12, TimeUnit.HOURS);
    }

    public InetAddress resolve(NtpServer server) throws UnknownHostException {
        return dnsCache.computeIfAbsent(server.getHost(), host -> {
            try {
                return InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                throw new ServerUnreachableException(host);
            }
        });
    }

    private void refreshDns() {
        dnsCache.replaceAll((host, addr) -> {
            try {
                return InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                return addr; // 保留旧地址
            }
        });
    }

    public NtpServer selectBestServer() {
        return servers.stream()
            .filter(NtpServer::isAvailable)
            .min(Comparator.comparingInt(NtpServer::getStratum)
                .thenComparing(s -> estimateNetworkLatency(s.getHost())))
            .orElseThrow(() -> new NtpException("No available NTP servers", null));
    }

    private long estimateNetworkLatency(String host) {
        // 实现网络延迟探测逻辑（网页8）
        return 0; // 简化的实现
    }
}

// 主客户端实现
public class NTPClient {
    private static final NTPUDPClient client = new NTPUDPClient();
    private static final ClockCompensator compensator = new ClockCompensator();
    private static final NtpRouter router = new NtpRouter(Arrays.asList(
        new NtpServer("ntp1.aliyun.com", 1),
        new NtpServer("ntp2.tencent.com", 2),
        new NtpServer("pool.ntp.org", 3)
    ));

    static {
        client.setDefaultTimeout(5000);
        client.setVersion(4); // 强制NTPv4（网页4）
    }

    public static long getNetworkTime() {
        try {
            NtpServer server = router.selectBestServer();
            InetAddress address = router.resolve(server);
            
            TimeInfo info = client.getTime(address);
            info.computeDetails();
            
            // 协议校验（网页10）
            validateResponse(info.getMessage());
            
            return compensator.compensate(
                info.getMessage().getTransmitTimeStamp().getTime(),
                info.getDelay()
            );
        } catch (IOException e) {
            throw new ServerUnreachableException("Network failure");
        }
    }

    private static void validateResponse(NtpV3Packet packet) {
        // 校验跳数（网页9）
        if(packet.getStratum() > 3) {
            throw new StratumViolationException(packet.getStratum());
        }
        
        // 校验Kiss-o'-Death报文（网页8）
        if(packet.getReferenceIdString().startsWith("RATE")) {
            throw new NtpException("Server rate limited", null);
        }
    }
    
    public static void shutdown() {
        client.close();
    }
}