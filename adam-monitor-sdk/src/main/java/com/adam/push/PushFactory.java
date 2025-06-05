package com.adam.push;

import com.adam.push.impl.KafkaPush;

// 推送工厂
public class PushFactory {
    // 根据推送类型创建推送对象
    public static IPush createPush(String type, String host, int port) {
        IPush push;
        switch (type.toLowerCase()) {
            case "kafka":
                push = new KafkaPush();
                break;
            default:
                throw new IllegalArgumentException("Unsupported push type: " + type);
        }
        push.open(host, port);  // 初始化连接
        return push;
    }
}
