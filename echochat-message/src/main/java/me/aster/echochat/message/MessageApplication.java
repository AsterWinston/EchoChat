package me.aster.echochat.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EchoChat消息微服务的Spring Boot入口点。
 * 扫描本地包和共享的common模块。
 * @author AsterWinston
 */
@SpringBootApplication(scanBasePackages = {"me.aster.echochat.message", "me.aster.echochat.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class MessageApplication {

    /**
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
}