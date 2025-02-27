package com.adam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.adam.mapper")
public class Application {


    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);  // 启动应用
    }


}
