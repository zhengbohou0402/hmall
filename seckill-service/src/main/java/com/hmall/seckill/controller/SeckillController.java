package com.hmall.seckill.controller;

import com.hmall.common.utils.RedisStockUtils;
import com.hmall.seckill.constants.SeckillConstants;
import com.hmall.seckill.domain.dto.SeckillActivityFormDTO;
import com.hmall.seckill.domain.po.SeckillActivity;
import com.hmall.seckill.domain.po.SeckillOrder;
import com.hmall.seckill.service.ISeckillOrderService;
import com.hmall.seckill.service.ISeckillService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "秒杀相关接口")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final ISeckillService seckillService;
    private final ISeckillOrderService seckillOrderService;
    private final RedisStockUtils redisStockUtils;

    // TODO Phase4 RBAC：创建活动应限制为 ADMIN 角色，当前仅要求登录
    @Operation(summary = "创建秒杀活动")
    @PostMapping("/activities")
    public Long createActivity(@RequestBody @Validated SeckillActivityFormDTO formDTO) {
        return seckillService.createActivity(formDTO);
    }

    @Operation(summary = "查询进行中的秒杀活动")
    @GetMapping("/activities")
    public List<SeckillActivity> listActive() {
        return seckillService.listActive();
    }

    @Operation(summary = "查询活动剩余名额")
    @GetMapping("/activities/{id}/stock")
    public Integer queryStock(@PathVariable("id") Long activityId) {
        return redisStockUtils.getStock(SeckillConstants.stockKey(activityId));
    }

    @Operation(summary = "秒杀下单，网关对该接口有更严格的限流")
    @PostMapping("/activities/{id}/buy")
    public Long seckill(@PathVariable("id") Long activityId) {
        return seckillService.seckill(activityId);
    }

    @Operation(summary = "查询秒杀订单结果")
    @GetMapping("/orders/{id}")
    public SeckillOrder queryOrder(@PathVariable("id") Long orderId) {
        return seckillOrderService.getById(orderId);
    }
}
