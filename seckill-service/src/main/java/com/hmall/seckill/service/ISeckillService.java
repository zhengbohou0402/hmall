package com.hmall.seckill.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.seckill.domain.dto.SeckillActivityFormDTO;
import com.hmall.seckill.domain.po.SeckillActivity;

import java.util.List;

public interface ISeckillService extends IService<SeckillActivity> {

    Long createActivity(SeckillActivityFormDTO formDTO);

    List<SeckillActivity> listActive();

    /**
     * 秒杀下单，返回秒杀订单id（此刻 Redis 已预扣成功，DB 落库走异步）
     */
    Long seckill(Long activityId);
}
