package me.aster.echochat.common.config;

import me.aster.echochat.common.util.SensitiveWordFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring配置类，创建从classpath资源 {@code sensitive_words.txt} 初始化的
 * {@link SensitiveWordFilter} Bean。
 * @author AsterWinston
 */
@Configuration
public class SensitiveWordConfig {

    /**
     * @return 使用classpath资源 {@code sensitive_words.txt} 中的词预填充的
     *         {@link SensitiveWordFilter}
     */
    @Bean
    public SensitiveWordFilter sensitiveWordFilter() {
        return SensitiveWordFilter.fromClasspath("sensitive_words.txt");
    }
}