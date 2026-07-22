package me.aster.echochat.common.config;

import feign.RequestInterceptor;
import me.aster.echochat.common.security.InternalAuthConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign配置，将共享内部令牌附加到每个发出的服务间请求上，
 * 以便下游的 {@code /internal/**} 端点
 * （由 {@link me.aster.echochat.common.security.InternalAuthFilter} 保护）
 * 能够认证调用方。
 * @author AsterWinston
 */
@Configuration
public class InternalFeignConfig {

    /** 共享内部令牌，从配置中注入。 */
    @Value("${" + InternalAuthConstants.INTERNAL_TOKEN_PROPERTY + ":}")
    private String internalToken;

    /**
     * @return 一个 {@link RequestInterceptor}，将所有Feign调用添加内部令牌头
     */
    @Bean
    public RequestInterceptor internalTokenRequestInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header(InternalAuthConstants.INTERNAL_TOKEN_HEADER, internalToken);
            }
        };
    }
}