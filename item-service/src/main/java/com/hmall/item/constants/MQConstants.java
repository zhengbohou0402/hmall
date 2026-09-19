package com.hmall.item.constants;

public class MQConstants {
    //商品交换机
    public static final String ITEM_EXCHANGE_NAME = "item.direct";
    //商品队列名
    public static final String DELAY_ORDER_QUEUE_NAME = "item.queue";
    //商品的RoutingKey
    public static final String ITEM_QUERY_KEY = "item.query";

    // review-service 发布的评论创建事件，用于增量更新 item.comment_count
    public static final String REVIEW_EXCHANGE_NAME = "review.direct";
    public static final String REVIEW_CREATE_KEY = "review.create";
    public static final String REVIEW_QUEUE_NAME = "item.review.queue";
}
