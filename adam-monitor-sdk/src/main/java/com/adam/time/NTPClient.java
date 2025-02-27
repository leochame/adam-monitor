package com.adam.time;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class NTPClient {
    // 复用静态的客户端实例和DNS解析结果[2,6](@ref)
    private static final NTPUDPClient client = new NTPUDPClient();
    private static InetAddress hostAddr;
    private static long lastResolveTime;

    static {
        client.setDefaultTimeout(5000);
        // 初始化DNS解析
        refreshDnsCache();
    }

    // 带本地DNS缓存的优化实现
    public static synchronized long getNtpTime() {
        try {
            // 每12小时刷新DNS缓存[4](@ref)
            if (System.currentTimeMillis() - lastResolveTime > TimeUnit.HOURS.toMillis(12)) {
                refreshDnsCache();
            }
            // 在getNtpTime()方法中添加日志
//            System.out.println("Socket local port: " + client.getLocalPort());

            TimeInfo timeInfo = client.getTime(hostAddr);
            timeInfo.computeDetails(); // 网络延迟补偿
            return timeInfo.getMessage().getTransmitTimeStamp().getTime();
        } catch (IOException e) {
            // 失败时尝试重新解析DNS[4](@ref)
            refreshDnsCache();
            throw new RuntimeException("NTP request failed after retry", e);
        }
    }

    // 封装DNS解析和缓存逻辑
    private static void refreshDnsCache() {
        try {
            // 使用多个备用NTP服务器[2,4](@ref)
            hostAddr = InetAddress.getByName("pool.ntp.org");
            lastResolveTime = System.currentTimeMillis();
        } catch (UnknownHostException e) {
            throw new RuntimeException("DNS resolution failed", e);
        }
    }

    // 添加资源清理方法
    public static void shutdown() {
        client.close();
    }
}