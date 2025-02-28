package com.adam.ntp.network;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.IOException;
import java.net.InetAddress;

// 网络通信层（网页3的UDP通信实现）
public class NtpNetworkClient {
    private final NTPUDPClient client = new NTPUDPClient();
    
    public NtpNetworkClient() {
        client.setDefaultTimeout(5000);
        client.setVersion(4); // 强制NTPv4
    }
    
    public TimeInfo requestTime(InetAddress address) throws IOException {
        return client.getTime(address);
    }
}
