package com.hmall.review.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论创建后发布的 MQ 事件，item-service 订阅它来增量更新 item.comment_count
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewCreatedEvent {
    private Long itemId;
}
