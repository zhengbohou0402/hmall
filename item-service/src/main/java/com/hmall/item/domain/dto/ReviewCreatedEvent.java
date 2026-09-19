package com.hmall.item.domain.dto;

import lombok.Data;

/**
 * review-service 发布的评论创建事件，字段结构需与 review-service 侧的
 * com.hmall.review.domain.dto.ReviewCreatedEvent 保持一致（两个服务各自维护自己的 DTO 副本，
 * 与本项目其它跨服务 MQ 消息的既有做法一致，不引入共享消息模块）
 */
@Data
public class ReviewCreatedEvent {
    private Long itemId;
}
