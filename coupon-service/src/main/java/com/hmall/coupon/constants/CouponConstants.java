package com.hmall.coupon.constants;

public class CouponConstants {
    /** 券模板剩余可领数量，复用秒杀那套 Lua 原子扣减 */
    public static final String STOCK_KEY_PREFIX = "coupon:stock:";
    /** 已领用户集合，用于每人限领 1 张的场景 */
    public static final String USER_SET_KEY_PREFIX = "coupon:users:";

    public static String stockKey(Long templateId) {
        return STOCK_KEY_PREFIX + templateId;
    }

    public static String userSetKey(Long templateId) {
        return USER_SET_KEY_PREFIX + templateId;
    }
}
