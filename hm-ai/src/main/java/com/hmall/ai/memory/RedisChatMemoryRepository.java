package com.hmall.ai.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的会话记忆存储实现。
 * <p>
 * 本地无 Redis 时优雅降级（静默返回空/跳过保存），不阻断对话主流程。
 */
@Slf4j
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    /** 默认 key 前缀 */
    public static final String DEFAULT_PREFIX = "HMALL:AI:CHAT:";

    private final String prefix;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisChatMemoryRepository(StringRedisTemplate stringRedisTemplate) {
        this(DEFAULT_PREFIX, stringRedisTemplate);
    }

    public RedisChatMemoryRepository(String prefix, StringRedisTemplate stringRedisTemplate) {
        this.prefix = prefix;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public List<String> findConversationIds() {
        try {
            Set<String> keys = stringRedisTemplate.keys(prefix + "*");
            if (keys == null) {
                return List.of();
            }
            return keys.stream()
                    .map(key -> key.substring(prefix.length()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("查询会话 id 列表失败（Redis 不可用？）: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            String redisKey = getKey(conversationId);
            List<String> jsons = stringRedisTemplate.opsForList().range(redisKey, 0, -1);
            if (jsons == null || jsons.isEmpty()) {
                log.debug("会话记忆为空: conversationId={}", conversationId);
                return List.of();
            }
            List<Message> messages = new java.util.ArrayList<>();
            for (String json : jsons) {
                try {
                    messages.add(MessageUtil.toMessage(json));
                } catch (Exception e) {
                    log.warn("反序列化会话消息失败: {}，json={}", e.getMessage(), json.substring(0, Math.min(100, json.length())));
                }
            }
            log.debug("读取会话记忆 {}: 原始 {} 条, 成功反序列化 {} 条", conversationId, jsons.size(), messages.size());
            return messages;
        } catch (Exception e) {
            log.warn("查询会话记忆失败（Redis 不可用？）: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        try {
            String redisKey = getKey(conversationId);
            // 保存时传入全部消息（含历史），先删旧再写新
            deleteByConversationId(conversationId);
            messages.forEach(message -> stringRedisTemplate.opsForList().rightPush(redisKey, MessageUtil.toJson(message)));
        } catch (Exception e) {
            log.warn("保存会话记忆失败（Redis 不可用？）: {}", e.getMessage());
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            stringRedisTemplate.delete(getKey(conversationId));
        } catch (Exception e) {
            log.warn("删除会话记忆失败（Redis 不可用？）: {}", e.getMessage());
        }
    }

    private String getKey(String conversationId) {
        return prefix + conversationId;
    }
}
