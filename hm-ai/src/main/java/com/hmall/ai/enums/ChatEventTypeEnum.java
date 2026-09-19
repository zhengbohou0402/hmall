package com.hmall.ai.enums;

import lombok.Getter;

/**
 * 聊天事件类型
 */
@Getter
public enum ChatEventTypeEnum {

    /** 数据（大模型输出的内容片段） */
    DATA(1001),
    /** 输出结束标记 */
    STOP(1002);

    private final int value;

    ChatEventTypeEnum(int value) {
        this.value = value;
    }
}
