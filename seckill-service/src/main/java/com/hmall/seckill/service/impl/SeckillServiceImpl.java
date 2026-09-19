package com.hmall.seckill.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.RabbitMQHelper;
import com.hmall.common.utils.RedisStockUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.seckill.constants.SeckillConstants;
import com.hmall.seckill.domain.dto.SeckillActivityFormDTO;
import com.hmall.seckill.domain.dto.SeckillOrderEvent;
import com.hmall.seckill.domain.po.SeckillActivity;
import com.hmall.seckill.mapper.SeckillActivityMapper;
import com.hmall.seckill.service.ISeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl extends ServiceImpl<SeckillActivityMapper, SeckillActivity> implements ISeckillService {

    private final Snowflake snowflake;
    private final RedisStockUtils redisStockUtils;
    private final RabbitMQHelper rabbitMQHelper;

    @Override
    public Long createActivity(SeckillActivityFormDTO formDTO) {
        LocalDateTime now = LocalDateTime.now();
        SeckillActivity activity = new SeckillActivity()
                .setId(snowflake.nextId())
                .setItemId(formDTO.getItemId())
                .setSeckillPrice(formDTO.getSeckillPrice())
                .setStockQuota(formDTO.getStockQuota())
                .setStartTime(formDTO.getStartTime() == null ? now : formDTO.getStartTime())
                .setEndTime(formDTO.getEndTime())
                .setStatus(2);
        save(activity);
        // 活动创建即把名额预热进 Redis，秒杀请求只碰 Redis 不碰 MySQL
        redisStockUtils.initStock(SeckillConstants.stockKey(activity.getId()), formDTO.getStockQuota());
        return activity.getId();
    }

    @Override
    public List<SeckillActivity> listActive() {
        return lambdaQuery().eq(SeckillActivity::getStatus, 2).list();
    }

    @Override
    public Long seckill(Long activityId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        SeckillActivity activity = getById(activityId);
        if (activity == null) {
            throw new BadRequestException("秒杀活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (activity.getStartTime() != null && now.isBefore(activity.getStartTime())) {
            throw new BizIllegalException("秒杀尚未开始");
        }
        if (activity.getEndTime() != null && now.isAfter(activity.getEndTime())) {
            throw new BizIllegalException("秒杀已结束");
        }

        // 一人一单去重 + 库存扣减合并成一次 Lua 原子执行，杜绝并发下的超卖和重复下单
        long result = redisStockUtils.deductOncePerUser(
                SeckillConstants.stockKey(activityId),
                SeckillConstants.userSetKey(activityId),
                1, userId);
        if (result == -2L) {
            throw new BizIllegalException("您已参与过本次秒杀");
        }
        if (result == 0L) {
            throw new BizIllegalException("已抢完");
        }
        if (result == -1L) {
            throw new BizIllegalException("秒杀活动未开放");
        }

        // 预扣成功，订单id当场确定，真正落库和扣减真实库存交给 MQ 异步处理
        Long orderId = snowflake.nextId();
        rabbitMQHelper.sendMessage(
                SeckillConstants.SECKILL_EXCHANGE_NAME,
                SeckillConstants.SECKILL_ORDER_KEY,
                new SeckillOrderEvent(orderId, activityId, userId,
                        activity.getItemId(), activity.getSeckillPrice())
        );
        return orderId;
    }
}
