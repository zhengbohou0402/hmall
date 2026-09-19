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
@TableName("coupon_issued")
public class CouponIssued implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long templateId;

    private Long userId;

    /**
     * 优惠码，用户下单时提交这个值
     */
    private String couponCode;

    /**
     * 状态 1未使用 2已使用 3已过期
     */
    private Integer status;

    private LocalDateTime issueTime;

    private LocalDateTime useTime;

    /**
     * 核销时关联的订单id
     */
    private Long orderId;
}
