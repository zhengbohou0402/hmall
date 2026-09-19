package com.hmall.ai.service.impl;

import com.hmall.ai.enums.ChatEventTypeEnum;
import com.hmall.ai.service.ChatService;
import com.hmall.ai.vo.ChatEventVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 聊天服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;

    /** 生成状态标记（sessionId -> 是否正在生成），用于停止生成 */
    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    /** 商城 AI 导购系统提示词 */
    private static final String SYSTEM_PROMPT = """
            你是一位热情、专业的黑马商城 AI 导购助手。
            你的职责是帮助用户解答商品咨询、推荐合适的商品、引导下单。
            对话上下文会附上从商品库检索到的相关商品信息（含名称、品牌、分类、规格、价格等）。
            当用户表达购物需求或询问商品时，优先基于检索到的商品信息进行推荐，说明推荐理由和卖点。
            请用简洁、友好的中文回答。
            """;

    @Override
    public Flux<ChatEventVO> chat(String question, String sessionId) {
        // 会话记忆的对话 id（无会话时使用默认会话）
        String conversationId = StringUtils.hasText(sessionId) ? sessionId : "default";

        return this.chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(question)
                .stream()
                .chatResponse()
                .doFirst(() -> GENERATE_STATUS.put(sessionId, true))
                .doOnError(throwable -> GENERATE_STATUS.remove(sessionId))
                .doOnComplete(() -> GENERATE_STATUS.remove(sessionId))
                .takeWhile(response -> Optional.ofNullable(GENERATE_STATUS.get(sessionId)).orElse(false))
                .map(chatResponse -> {
                    String text = chatResponse.getResult().getOutput().getText();
                    return ChatEventVO.builder()
                            .eventData(text)
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .build();
                })
                .concatWith(Flux.just(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.STOP.getValue())
                        .build()));
    }

    @Override
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }
}
