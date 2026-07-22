package me.aster.echochat.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 用户微服务的 Spring Boot 入口。
 * 管理用户账户、个人资料、好友关系和在线状态。
 * @author AsterWinston
 */
@SpringBootApplication(scanBasePackages = {"me.aster.echochat.user", "me.aster.echochat.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class UserApplication {

    /**
     * 启动用户服务应用程序。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
