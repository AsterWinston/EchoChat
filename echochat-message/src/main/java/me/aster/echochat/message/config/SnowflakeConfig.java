package me.aster.echochat.message.config;

import me.aster.echochat.common.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake分布式ID生成器的配置，提供worker和datacenter ID。
 * @author AsterWinston
 */
@Configuration
public class SnowflakeConfig {

    /** Snowflake worker ID，默认3 */
    @Value("${snowflake.worker-id:3}")
    private long workerId;

    /** Snowflake datacenter ID，默认1 */
    @Value("${snowflake.datacenter-id:1}")
    private long datacenterId;

    /**
     * @return 配置好的{@link me.aster.echochat.common.util.SnowflakeIdGenerator}实例
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }
}