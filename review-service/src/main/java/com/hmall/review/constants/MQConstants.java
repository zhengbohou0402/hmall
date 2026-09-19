package com.hmall.review.constants;

public class MQConstants {
    // 评论交换机，item-service 订阅它来更新 item.comment_count
    public static final String REVIEW_EXCHANGE_NAME = "review.direct";
    public static final String REVIEW_CREATE_KEY = "review.create";
}
