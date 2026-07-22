package me.aster.echochat.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * EchoChat API网关应用程序，提供统一路由和认证功能。
 * @author AsterWinston
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {

    /**
     * 以Spring Boot应用程序方式启动EchoChat网关。
     *
     * @param args 传递给应用程序的命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}