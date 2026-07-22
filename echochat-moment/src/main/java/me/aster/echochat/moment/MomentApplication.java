package me.aster.echochat.moment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Moment（动态/时间线）微服务的Spring Boot入口。
 * @author AsterWinston
 */
@SpringBootApplication(scanBasePackages = {"me.aster.echochat.moment", "me.aster.echochat.common"})
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class MomentApplication {

    /**
     * 通过Spring Boot启动Moment微服务。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(MomentApplication.class, args);
    }
}
