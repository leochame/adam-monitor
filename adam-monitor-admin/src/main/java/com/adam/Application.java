package com.adam;

import com.adam.listener.LogMessage;
import com.adam.listener.MonitorLogListener;
import com.adam.service.LogAnalyticalService;
import lombok.Data;
import org.redisson.api.RedissonClient;
import org.redisson.api.RTopic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SpringBootApplication
public class Application {



    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);  // 启动应用
    }


}
