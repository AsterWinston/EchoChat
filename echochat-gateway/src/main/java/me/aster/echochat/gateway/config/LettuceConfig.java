package me.aster.echochat.gateway.config;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 配置Lettuce Redis客户端，增强其弹性。
 * 启用自动重连并使用默认的断连命令行为，使Redis的短暂中断不会导致级联故障。
 * 短命令超时确保当Redis真正宕机时请求能快速失败。
 * @author AsterWinston
 */
@Configuration
public class LettuceConfig {

    /**
     * @return 配置Lettuce自动重连、默认断连行为和2秒命令超时的自定义器
     */
    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
        return builder -> builder
                .commandTimeout(Duration.ofSeconds(2))
                .clientOptions(
                        ClientOptions.builder()
                                .autoReconnect(true)
                                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.DEFAULT)
                                .build()
                );
    }
}