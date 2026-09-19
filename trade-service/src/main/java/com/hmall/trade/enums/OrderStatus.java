package com.hmall.trade.enums;

import com.hmall.common.exception.BizIllegalException;
import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Order.status 之前只是一个裸 int，只有 1→2（支付）、1→5（取消）两条转移在代码里真正出现过，
 * 3/4/6 这几个值虽然在字段注释里写了含义，但从未被赋过值。这里把允许的转移关系显式化，
 * 未来任何状态变更都必须通过 {@link #checkTransition} 校验，防止出现非法跳变。
 */
@Getter
public enum OrderStatus {
    UNPAID(1, "未付款"),
    PAID(2, "已付款,未发货"),
    SHIPPED(3, "已发货,未确认"),
    CONFIRMED(4, "确认收货,交易成功"),
    CLOSED(5, "交易取消,订单关闭"),
    COMMENTED(6, "交易结束,已评价"),
    ;

    private final int value;
    private final String desc;

    OrderStatus(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    private static final Map<Integer, OrderStatus> VALUE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(OrderStatus::getValue, s -> s));

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            UNPAID, EnumSet.of(PAID, CLOSED),
            PAID, EnumSet.of(SHIPPED),
            SHIPPED, EnumSet.of(CONFIRMED),
            CONFIRMED, EnumSet.of(COMMENTED),
            CLOSED, EnumSet.noneOf(OrderStatus.class),
            COMMENTED, EnumSet.noneOf(OrderStatus.class)
    );

    public static OrderStatus of(Integer value) {
        OrderStatus status = value == null ? null : VALUE_MAP.get(value);
        if (status == null) {
            throw new BizIllegalException("非法的订单状态：" + value);
        }
        return status;
    }

    /**
     * 校验 from -> to 是否是允许的状态转移，不允许则抛出异常，调用方无需再自行判断
     */
    public static void checkTransition(Integer from, OrderStatus to) {
        OrderStatus current = of(from);
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, EnumSet.noneOf(OrderStatus.class)).contains(to)) {
            throw new BizIllegalException("订单状态不允许从[" + current.getDesc() + "]变更为[" + to.getDesc() + "]");
        }
    }
}
