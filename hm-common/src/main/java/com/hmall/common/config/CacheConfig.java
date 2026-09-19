package com.hmall.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmall.common.utils.MultiLevelCacheUtils;
import com.hmall.common.utils.RedisStockUtils;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 只有引入了 spring-boot-starter-data-redis 的服务才会装配多级缓存工具，
 * 未接入 Redis 的服务（trade/pay-service 等）不受影响。
 * 必须显式排在 RedisAutoConfiguration 之后，否则 spring.factories 里注册的这个
 * legacy 风格 auto-configuration 可能在 StringRedisTemplate 注册前就被处理，
 * 导致下面的 @ConditionalOnBean(StringRedisTemplate.class) 误判为不存在
 */
@Configuration
@ConditionalOnClass(StringRedisTemplate.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class CacheConfig {

    @Bean
    @ConditionalOnMissingBean
    public Cache<String, Object> localCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(2))
                .maximumSize(10_000)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public MultiLevelCacheUtils multiLevelCacheUtils(Cache<String, Object> localCache,
                                                       StringRedisTemplate redisTemplate,
                                                       ObjectMapper objectMapper) {
        return new MultiLevelCacheUtils(localCache, redisTemplate, objectMapper);
    }

    /**
     * 秒杀/优惠券共用的 Redis Lua 原子扣减工具
     */
    @Bean
    @ConditionalOnMissingBean
    public RedisStockUtils redisStockUtils(StringRedisTemplate redisTemplate) {
        return new RedisStockUtils(redisTemplate);
    }
}
