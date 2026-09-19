package com.hmall.ai.memory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 消息与 JSON 字符串转换工具（用于 Redis 存储）。
 */
public class MessageUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MessageUtil() {
    }

    /**
     * Message → JSON 字符串
     */
    public static String toJson(Message message) {
        MyMessage myMessage = new MyMessage();
        myMessage.setMessageType(message.getMessageType().name());
        myMessage.setTextContent(message.getText());
        if (message.getMetadata() != null) {
            myMessage.setMetadata(message.getMetadata());
        }
        if (message instanceof AssistantMessage assistantMessage) {
            myMessage.setToolCalls(assistantMessage.getToolCalls());
        }
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            myMessage.setToolResponses(toolResponseMessage.getResponses());
        }
        try {
            return MAPPER.writeValueAsString(myMessage);
        } catch (Exception e) {
            throw new RuntimeException("消息序列化失败", e);
        }
    }

    /**
     * JSON 字符串 → Message
     */
    public static Message toMessage(String json) {
        try {
            MyMessage myMessage = MAPPER.readValue(json, MyMessage.class);
            MessageType type = MessageType.valueOf(myMessage.getMessageType());
            return switch (type) {
                case SYSTEM -> new SystemMessage(myMessage.getTextContent());
                case USER -> UserMessage.builder()
                        .text(myMessage.getTextContent())
                        .metadata(myMessage.getMetadata())
                        .build();
                case ASSISTANT -> new AssistantMessage(myMessage.getTextContent(), myMessage.getMetadata(), myMessage.getToolCalls());
                case TOOL -> new ToolResponseMessage(myMessage.getToolResponses(), myMessage.getMetadata());
            };
        } catch (Exception e) {
            throw new RuntimeException("消息反序列化失败: " + json, e);
        }
    }
}
