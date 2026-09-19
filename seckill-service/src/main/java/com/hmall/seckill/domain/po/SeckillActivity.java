package com.hmall.seckill.domain.po;

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
@TableName("seckill_activity")
public class SeckillActivity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 活动id，雪花生成
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /**
     * 参与秒杀的商品id
     */
    private Long itemId;

    /**
     * 秒杀价（分），独立于商品原价
     */
    private Integer seckillPrice;

    /**
     * 秒杀名额，独立于 item.stock，避免直接打热点行
     */
    private Integer stockQuota;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /**
     * 状态 1未开始 2进行中 3已结束
     */
    private Integer status;

    private LocalDateTime createTime;
}
