package com.hmall.common.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;

/**
 * Redis 原子扣减工具：秒杀库存、优惠券库存都用同一套 Lua 脚本，
 * 把"读取-判断-扣减"三步压成一次原子执行，避免高并发下的超卖/超发。
 */
@RequiredArgsConstructor
public class RedisStockUtils {

    private final StringRedisTemplate redisTemplate;

    /**
     * 库存原子扣减。返回值：1=扣减成功，0=库存不足，-1=库存key不存在(活动未初始化或已结束)
     */
    private static final RedisScript<Long> DEDUCT_SCRIPT = new DefaultRedisScript<>(
            "local stock = redis.call('get', KEYS[1]) " +
            "if stock == false then return -1 end " +
            "if tonumber(stock) < tonumber(ARGV[1]) then return 0 end " +
            "redis.call('decrby', KEYS[1], ARGV[1]) " +
            "return 1",
            Long.class);

    /**
     * 一人一单去重 + 库存扣减合并成一次原子操作。
     * 返回值：1=成功，0=库存不足，-1=库存key不存在，-2=该用户已抢过
     */
    private static final RedisScript<Long> DEDUCT_ONCE_PER_USER_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('sismember', KEYS[2], ARGV[2]) == 1 then return -2 end " +
            "local stock = redis.call('get', KEYS[1]) " +
            "if stock == false then return -1 end " +
            "if tonumber(stock) < tonumber(ARGV[1]) then return 0 end " +
            "redis.call('decrby', KEYS[1], ARGV[1]) " +
            "redis.call('sadd', KEYS[2], ARGV[2]) " +
            "return 1",
            Long.class);

    public long deduct(String stockKey, int num) {
        Long result = redisTemplate.execute(DEDUCT_SCRIPT,
                Collections.singletonList(stockKey), String.valueOf(num));
        return result == null ? -1L : result;
    }

    public long deductOncePerUser(String stockKey, String userSetKey, int num, Long userId) {
        Long result = redisTemplate.execute(DEDUCT_ONCE_PER_USER_SCRIPT,
                List.of(stockKey, userSetKey), String.valueOf(num), String.valueOf(userId));
        return result == null ? -1L : result;
    }

    /**
     * 初始化/重置库存，活动上架时调用
     */
    public void initStock(String stockKey, int stock) {
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
    }

    public Integer getStock(String stockKey) {
        String v = redisTemplate.opsForValue().get(stockKey);
        return v == null ? null : Integer.valueOf(v);
    }

    /**
     * 扣减后的补偿回滚，用于下游落库失败时把 Redis 预扣的库存还回去
     */
    public void restore(String stockKey, String userSetKey, int num, Long userId) {
        redisTemplate.opsForValue().increment(stockKey, num);
        if (userSetKey != null && userId != null) {
            redisTemplate.opsForSet().remove(userSetKey, String.valueOf(userId));
        }
    }
}
