package com.hmall.coupon.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("coupon_template")
public class CouponTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String name;

    /**
     * 优惠类型 1满减 2折扣
     */
    private Integer discountType;

    /**
     * 满减为减免金额(分)，折扣为折扣率(如85表示8.5折)
     */
    private Integer discountValue;

    /**
     * 使用门槛金额(分)，订单金额需达到此值
     */
    private Integer thresholdAmount;

    private Integer totalQuantity;

    private Integer issuedQuantity;

    /**
     * 每人限领数量
     */
    private Integer perUserLimit;

    private LocalDateTime validStart;

    private LocalDateTime validEnd;

    /**
     * 状态 1可领取 2已下架
     */
    private Integer status;

    private LocalDateTime createTime;
}
