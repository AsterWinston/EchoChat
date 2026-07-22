package me.aster.echochat.file.config;

import me.aster.echochat.common.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分布式Snowflake ID生成器配置。
 * @author AsterWinston
 */
@Configuration
public class SnowflakeConfig {

    /** Snowflake工作节点ID，默认为7 */
    @Value("${snowflake.worker-id:7}")
    private long workerId;

    /** Snowflake数据中心ID，默认为1 */
    @Value("${snowflake.datacenter-id:1}")
    private long datacenterId;

    /**
     * @return 根据工作节点ID和数据中心ID配置的 {@link SnowflakeIdGenerator} bean
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator(workerId, datacenterId);
    }
}
