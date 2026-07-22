package me.aster.echochat.user.config;

import me.aster.echochat.common.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Snowflake 分布式 ID 生成器的配置。
 * 基于可配置的工作机器 ID 和数据中心 ID 提供 SnowflakeIdGenerator Bean。
 * @author AsterWinston
 */
@Configuration
public class SnowflakeConfig {

    /** 工作机器 ID，默认为 1。 */
    @Value("${snowflake.worker-id:1}")
    private long workerId;

    /** 数据中心 ID，默认为 1。 */
    @Value("${snowflake.datacenter-id:1}")
    private long datacenterId;

    /**
     * 使用配置的工作机器 ID 和数据中心 ID 创建 SnowflakeIdGenerator Bean。
     *
     * @return 新的 SnowflakeIdGenerator 实例
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }
}
