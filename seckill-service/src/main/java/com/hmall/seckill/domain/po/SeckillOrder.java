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
@TableName("seckill_order")
public class SeckillOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 秒杀订单id，雪花生成，Redis 预扣成功时就确定，用于前端轮询结果
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long activityId;

    private Long userId;

    private Long itemId;

    private Integer seckillPrice;

    /**
     * 状态 1待处理(Redis已预扣) 2已创建(DB落库成功) 3失败已回滚
     */
    private Integer status;

    private LocalDateTime createTime;
}
