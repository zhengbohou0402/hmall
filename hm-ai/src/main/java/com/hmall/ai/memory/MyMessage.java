package com.hmall.ai.memory;

import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Map;

/**
 * 消息存储 DTO：统一承载 4 种消息（System/User/Assistant/Tool），
 * 通过 messageType 区分，用于 Redis 序列化存储。
 */
@Data
public class MyMessage {

    /** 消息类型：SYSTEM / USER / ASSISTANT / TOOL */
    private String messageType;

    private Map<String, Object> metadata = Map.of();

    /** 文本内容 */
    private String textContent;

    /** Assistant 消息的工具调用 */
    private List<AssistantMessage.ToolCall> toolCalls = List.of();

    /** Tool 消息的响应 */
    private List<ToolResponseMessage.ToolResponse> toolResponses = List.of();
}
