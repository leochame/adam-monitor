package com.adam.time;

import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;
import java.net.InetAddress;

public class NTPClient {
    public static void getNTPTime(){
        try {
            NTPUDPClient client = new NTPUDPClient();
            client.setDefaultTimeout(5000); // 设置超时时间
            InetAddress server = InetAddress.getByName("pool.ntp.org"); // 公共NTP服务器
            TimeInfo timeInfo = client.getTime(server); // 发送请求
            timeInfo.computeDetails(); // 计算延迟和偏移量
            long offset = timeInfo.getOffset(); // 本地时钟偏差（毫秒）
            System.out.println("服务器时间: " + timeInfo.getMessage().getTransmitTimeStamp().toDateString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {
            getNTPTime();
        }
    }
}                                                                   