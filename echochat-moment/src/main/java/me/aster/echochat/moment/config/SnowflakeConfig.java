package me.aster.echochat.moment.config;

import me.aster.echochat.common.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Moment服务的雪花ID生成器配置（worker-id=5）。
 * @author AsterWinston
 */
@Configuration
public class SnowflakeConfig {

    /** 分布式唯一ID生成的工作节点ID */
    @Value("${snowflake.worker-id:5}")
    private long workerId;

    /** 分布式唯一ID生成的数据中心ID */
    @Value("${snowflake.datacenter-id:1}")
    private long datacenterId;

    /** @return 为Moment服务配置的SnowflakeIdGenerator */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }
}
