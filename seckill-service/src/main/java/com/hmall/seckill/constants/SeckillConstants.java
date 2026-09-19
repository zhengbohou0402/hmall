package com.hmall.seckill.constants;

public class SeckillConstants {
    /** 活动库存key，Redis 预扣的对象，与 item.stock 解耦 */
    public static final String STOCK_KEY_PREFIX = "seckill:stock:";
    /** 已抢到的用户集合，用于一人一单去重 */
    public static final String USER_SET_KEY_PREFIX = "seckill:users:";

    public static final String SECKILL_EXCHANGE_NAME = "seckill.direct";
    public static final String SECKILL_ORDER_KEY = "seckill.order";
    public static final String SECKILL_ORDER_QUEUE_NAME = "seckill.order.queue";

    public static String stockKey(Long activityId) {
        return STOCK_KEY_PREFIX + activityId;
    }

    public static String userSetKey(Long activityId) {
        return USER_SET_KEY_PREFIX + activityId;
    }
}
