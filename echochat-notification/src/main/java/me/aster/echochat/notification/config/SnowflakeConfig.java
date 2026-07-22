package me.aster.echochat.notification.config;

import me.aster.echochat.common.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置类，为通知服务提供可配置worker ID和datacenter ID的{@link SnowflakeIdGenerator} Bean。
 * @author AsterWinston
 */
@Configuration
public class SnowflakeConfig {

    /** 雪花算法Worker ID，默认为9。 */
    @Value("${snowflake.worker-id:9}")
    private long workerId;

    /** 雪花算法数据中心ID，默认为1。 */
    @Value("${snowflake.datacenter-id:1}")
    private long datacenterId;

    /**
     * 创建雪花ID生成器Bean。
     *
     * @return 已配置的{@link SnowflakeIdGenerator}
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }
}
