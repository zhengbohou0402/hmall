package com.hmall.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天事件响应对象（SSE 输出）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEventVO {

    /** 事件数据（大模型输出的文本片段） */
    private String eventData;

    /** 事件类型（1001 数据 / 1002 结束） */
    private Integer eventType;
}
