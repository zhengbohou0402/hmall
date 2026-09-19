package com.hmall.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "hm.snowflake")
public class SnowflakeProperties {
    /**
     * 工作机器id，单机学习环境固定小整数即可，无需多实例动态协调
     */
    private long workerId = 1L;
    private long datacenterId = 1L;
}
