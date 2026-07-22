package me.aster.echochat.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Spring Boot文件存储微服务入口。
 * @author AsterWinston
 */
@SpringBootApplication(scanBasePackages = {"me.aster.echochat.file", "me.aster.echochat.common"})
@EnableDiscoveryClient
@EnableFeignClients
public class FileApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileApplication.class, args);
    }
}
