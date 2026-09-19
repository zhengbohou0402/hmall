package com.hmall.common.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.function.Function;

/**
 * 通用多级缓存：本地 Caffeine(L1) -> Redis(L2) -> 调用方提供的回源函数，逐级未命中回填。
 * 缓存正确性主要靠调用方在写操作完成后主动调用 evict，TTL 只是兜底，不是主要失效手段。
 */
@Slf4j
@RequiredArgsConstructor
public class MultiLevelCacheUtils {

    private final Cache<String, Object> localCache;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public <T> T get(String key, Class<T> type, Function<String, T> loader, Duration redisTtl) {
        Object cached = localCache.getIfPresent(key);
        if (cached != null) {
            return type.cast(cached);
        }
        String redisJson = redisTemplate.opsForValue().get(key);
        if (redisJson != null) {
            T value = readValue(key, redisJson, type);
            if (value != null) {
                localCache.put(key, value);
            }
            return value;
        }
        T loaded = loader.apply(key);
        if (loaded != null) {
            redisTemplate.opsForValue().set(key, writeValue(loaded), redisTtl);
            localCache.put(key, loaded);
        }
        return loaded;
    }

    public void evict(String key) {
        localCache.invalidate(key);
        redisTemplate.delete(key);
    }

    private <T> T readValue(String key, String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            log.warn("多级缓存反序列化失败，key={}", key, e);
            return null;
        }
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("多级缓存序列化失败", e);
        }
    }
}
