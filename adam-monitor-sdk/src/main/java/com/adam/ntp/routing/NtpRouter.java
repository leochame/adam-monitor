package com.adam.ntp.routing;

import com.adam.exception.NtpException;
import com.adam.exception.ServerUnreachableException;
import com.adam.ntp.core.NtpServer;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

// 文件4: NtpRouter.java
public class NtpRouter {
    private final List<NtpServer> servers;
    private final Map<String, InetAddress> dnsCache = new ConcurrentHashMap<>();

    private final ScheduledExecutorService dnsRefresher =
            Executors.newSingleThreadScheduledExecutor();

    public NtpRouter(List<NtpServer> servers) {
        this.servers = servers;
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
                return addr;
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
        // 简化的延迟估算实现
        return 0;
    }
}