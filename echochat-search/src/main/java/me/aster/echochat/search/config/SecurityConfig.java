package me.aster.echochat.search.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security配置：禁用CSRF、表单登录和HTTP Basic，设置无状态会话管理，允许所有请求。
 * @author AsterWinston
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置无状态、全部放行的安全过滤器链。
     *
     * @param http 要配置的{@link HttpSecurity}
     * @return 构建好的{@link SecurityFilterChain}
     * @throws Exception 配置过程中发生错误时抛出
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
