package com.hmall.common.config;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一的雪花ID生成器，供新引入的表使用（不强行迁移现有 AUTO/ASSIGN_ID 的表）
 */
@Configuration
@EnableConfigurationProperties(SnowflakeProperties.class)
public class SnowflakeConfig {

    @Bean
    @ConditionalOnMissingBean
    public Snowflake snowflake(SnowflakeProperties properties) {
        return IdUtil.getSnowflake(properties.getWorkerId(), properties.getDatacenterId());
    }
}
