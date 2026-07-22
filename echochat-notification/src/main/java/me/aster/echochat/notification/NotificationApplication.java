package me.aster.echochat.notification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * EchoChat通知服务的Spring Boot入口。
 * 启用服务发现、Feign客户端和MyBatis-Plus映射器扫描。
 * @author AsterWinston
 */
@SpringBootApplication(scanBasePackages = {"me.aster.echochat.notification", "me.aster.echochat.common"})
@MapperScan("me.aster.echochat.notification.mapper")
@EnableDiscoveryClient
@EnableFeignClients
public class NotificationApplication {

    /**
     * 启动通知服务应用程序。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
