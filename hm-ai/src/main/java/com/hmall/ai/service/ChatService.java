package com.hmall.ai.service;

import com.hmall.ai.vo.ChatEventVO;
import reactor.core.publisher.Flux;

/**
 * AI 聊天服务
 */
public interface ChatService {

    /**
     * 流式聊天
     *
     * @param question  用户问题
     * @param sessionId 会话 id
     * @return 回答内容流
     */
    Flux<ChatEventVO> chat(String question, String sessionId);

    /**
     * 停止生成
     *
     * @param sessionId 会话 id
     */
    void stop(String sessionId);
}
