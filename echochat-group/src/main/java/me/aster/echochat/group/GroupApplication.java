package me.aster.echochat.group;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Spring Boot群组微服务入口。
 * 扫描common库和group模块组件。
 * @author AsterWinston
 */
@SpringBootApplication(scanBasePackages = {"me.aster.echochat.group", "me.aster.echochat.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class GroupApplication {

    /**
     * 启动群组服务应用。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(GroupApplication.class, args);
    }
}
