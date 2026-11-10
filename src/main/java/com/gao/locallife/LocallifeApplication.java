package com.gao.locallife;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LocalLife 启动类。
 *
 * <p>M0 验收：启动后访问 GET /ping 返回 pong，GET /health 返回 mysql/redis 均为 ok。
 */
@EnableScheduling
@MapperScan("com.gao.locallife.mapper")
@SpringBootApplication
public class LocallifeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocallifeApplication.class, args);
    }
}
