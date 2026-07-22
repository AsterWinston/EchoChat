package me.aster.echochat.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * EchoChat 认证服务，提供登录、注册和令牌管理功能。
 * @author AsterWinston
 */
@SpringBootApplication(scanBasePackages = {"me.aster.echochat.auth", "me.aster.echochat.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class AuthApplication {

    /**
     * 启动认证服务应用程序。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
