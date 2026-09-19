package com.hmall.trade.controller;

import com.hmall.common.utils.BeanUtils;

import com.hmall.trade.domain.dto.OrderFormDTO;
import com.hmall.trade.domain.dto.OrderLogisticsDTO;
import com.hmall.trade.domain.po.OrderLogistics;
import com.hmall.trade.domain.vo.OrderVO;
import com.hmall.trade.service.IOrderLogisticsService;
import com.hmall.trade.service.IOrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

@Tag(name = "订单管理接口")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {
    private final IOrderService orderService;
    private final IOrderLogisticsService orderLogisticsService;

    @Operation(summary = "根据id查询订单")
    @GetMapping("{id}")
    public OrderVO queryOrderById(@Param ("订单id")@PathVariable("id") Long orderId) {
        return BeanUtils.copyBean(orderService.getById(orderId), OrderVO.class);
    }

    @Operation(summary = "创建订单")
    @PostMapping
    public Long createOrder(@RequestBody OrderFormDTO orderFormDTO){
        return orderService.createOrder(orderFormDTO);
    }

    @Operation(summary = "标记订单已支付")
    @Parameter(name = "orderId", description = "订单id", in = ParameterIn.PATH)
    @PutMapping("/{orderId}")
    public void markOrderPaySuccess(@PathVariable("orderId") Long orderId) {
        orderService.markOrderPaySuccess(orderId);
    }

    @Operation(summary = "发货")
    @PutMapping("/{id}/ship")
    public void ship(@PathVariable("id") Long orderId, @RequestBody OrderLogisticsDTO logisticsDTO) {
        orderService.ship(orderId, logisticsDTO);
    }

    @Operation(summary = "确认收货")
    @PutMapping("/{id}/confirm")
    public void confirmReceipt(@PathVariable("id") Long orderId) {
        orderService.confirmReceipt(orderId);
    }

    @Operation(summary = "查询订单物流信息")
    @GetMapping("/{id}/logistics")
    public OrderLogistics queryLogistics(@PathVariable("id") Long orderId) {
        return orderLogisticsService.getById(orderId);
    }

    @Operation(summary = "标记订单已评价，供review-service在提交评论时调用，内部校验订单归属与状态")
    @PutMapping("/{id}/comment")
    public void markOrderCommented(@PathVariable("id") Long orderId, @RequestParam("userId") Long userId) {
        orderService.markOrderCommented(orderId, userId);
    }
}
