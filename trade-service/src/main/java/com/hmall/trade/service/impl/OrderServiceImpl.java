package com.hmall.trade.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.CouponClient;
import com.hmall.api.client.ItemClient;
import com.hmall.api.client.PayClient;
import com.hmall.api.client.SearchClient;
import com.hmall.api.dto.CouponConsumeDTO;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.exception.ForbiddenException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.common.utils.RabbitMQHelper;

import com.hmall.common.utils.UserContext;
import com.hmall.trade.constants.MQConstants;
import com.hmall.trade.domain.dto.OrderFormDTO;
import com.hmall.trade.domain.dto.OrderLogisticsDTO;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.domain.po.OrderLogistics;
import com.hmall.trade.enums.OrderStatus;
import com.hmall.trade.mapper.OrderMapper;
import com.hmall.trade.service.IOrderDetailService;
import com.hmall.trade.service.IOrderLogisticsService;
import com.hmall.trade.service.IOrderService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final ItemClient itemClient;
    private final SearchClient searchClient;
    private final IOrderDetailService detailService;
    private final IOrderLogisticsService logisticsService;
    private final RabbitMQHelper rabbitMQHelper;
    private final RabbitTemplate rabbitTemplate;
    private final PayClient payClient;
    private final CouponClient couponClient;

    @Value("${hm.trade.order.pay-timeout:10000}")
    private long payTimeoutMillis;

    @Override
    @GlobalTransactional
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = searchClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }

        // 1.5.其它属性
        Long userId = UserContext.getUser();
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(userId);
        order.setStatus(OrderStatus.UNPAID.getValue());
        // 1.6.先落库拿到订单id，优惠券核销要把订单id写进核销记录
        order.setTotalFee(total);
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch(details);

        // 3.扣减库存
        try {
            itemClient.deductStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }

        // 4.优惠券核销放在扣减库存之后：核销发生在 coupon-service 自己的库里，本地事务回滚不了，
        // 一旦先核销再扣库存失败，用户的券就白扣了。放到最后能把这个窗口收到最小。
        // 注意：这一步让本就受 Seata 管理的分布式事务多了一次跨服务调用，会拉长持锁时间，
        // 秒杀/大促场景下需要留意，必要时改成下单前预校验+异步核销
        if (orderFormDTO.getCouponCode() != null && !orderFormDTO.getCouponCode().isEmpty()) {
            Integer discount = couponClient.validateAndConsume(new CouponConsumeDTO()
                    .setCouponCode(orderFormDTO.getCouponCode())
                    .setUserId(userId)
                    .setOrderAmount(total)
                    .setOrderId(order.getId()));
            if (discount != null && discount > 0) {
                int payable = Math.max(total - discount, 0);
                order.setTotalFee(payable);
                lambdaUpdate()
                        .set(Order::getTotalFee, payable)
                        .eq(Order::getId, order.getId())
                        .update();
            }
        }

        // 5.清理购物车商品
        //cartClient.deleteCartItemByIds(itemIds);
        //rabbitTemplate.convertAndSend("trade.topic", "order.create", itemIds);

        rabbitMQHelper.sendMessage("trade.topic", "order.create", itemIds);

        rabbitTemplate.convertAndSend(
                MQConstants.DELAY_EXCHANGE_NAME,
                MQConstants.DELAY_ORDER_KEY,
                order.getId(),
                message -> {
                    message.getMessageProperties().setDelayLong(payTimeoutMillis);
                    return message;
                }
        );
        return order.getId();
    }

    @Override
    public void markOrderPaySuccess(Long orderId) {
        Order order = getOrThrow(orderId);
        OrderStatus.checkTransition(order.getStatus(), OrderStatus.PAID);
        //避免线程安全问题
        // UPDATE `order` SET status = ? , pay_time = ? WHERE id = ? AND status = 1
        boolean updated = lambdaUpdate()
                .set(Order::getStatus, OrderStatus.PAID.getValue())
                .set(Order::getPayTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, OrderStatus.UNPAID.getValue())
                .update();
        if (!updated) {
            throw new BizIllegalException("订单状态已变化，标记支付成功失败");
        }
    }

    @Override
    public void cancelOrder(Long orderId) {
        Order order = getOrThrow(orderId);
        OrderStatus.checkTransition(order.getStatus(), OrderStatus.CLOSED);

        //修改交易订单信息为已关闭
        boolean updated = lambdaUpdate()
                .set(Order::getStatus, OrderStatus.CLOSED.getValue())
                .set(Order::getCloseTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, order.getStatus())
                .update();
        if (!updated) {
            throw new BizIllegalException("订单状态已变化，取消订单失败");
        }

        //修改支付订单状态为已取消
        payClient.updatePayOrderStatusByBizOrderNo(orderId, 5);

        //恢复订单中已经扣掉的库存
        List<OrderDetail> list = detailService.lambdaQuery()
                                                .eq(OrderDetail::getOrderId, orderId)
                                                .list();

        List<OrderDetailDTO> orderDetailDTOS = BeanUtils.copyList(list, OrderDetailDTO.class);
        itemClient.restoreStock(orderDetailDTOS);
    }

    @Override
    public void ship(Long orderId, OrderLogisticsDTO logisticsDTO) {
        Order order = getOrThrow(orderId);
        OrderStatus.checkTransition(order.getStatus(), OrderStatus.SHIPPED);

        OrderLogistics logistics = BeanUtils.copyProperties(logisticsDTO, OrderLogistics.class);
        logistics.setOrderId(orderId);
        logisticsService.saveOrUpdate(logistics);

        boolean updated = lambdaUpdate()
                .set(Order::getStatus, OrderStatus.SHIPPED.getValue())
                .set(Order::getConsignTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, order.getStatus())
                .update();
        if (!updated) {
            throw new BizIllegalException("订单状态已变化，发货失败");
        }
    }

    @Override
    public void confirmReceipt(Long orderId) {
        Order order = getOrThrow(orderId);
        OrderStatus.checkTransition(order.getStatus(), OrderStatus.CONFIRMED);

        boolean updated = lambdaUpdate()
                .set(Order::getStatus, OrderStatus.CONFIRMED.getValue())
                .set(Order::getEndTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, order.getStatus())
                .update();
        if (!updated) {
            throw new BizIllegalException("订单状态已变化，确认收货失败");
        }
    }

    @Override
    public void markOrderCommented(Long orderId, Long userId) {
        Order order = getOrThrow(orderId);
        // Feign 调用之间目前不可靠地转发登录态（AuthGlobalFilter 设的是 user-info 头，
        // 但 hm-api 的 DefaultFeignConfig 转发的是 user_id 头，两者对不上——见 hm-common/DefaultFeignConfig 的已知问题），
        // 所以这里要求调用方显式传入 userId 并在此校验订单归属，不依赖 UserContext 在被调用方的可用性
        if (userId == null || !userId.equals(order.getUserId())) {
            throw new ForbiddenException("无权评价该订单");
        }
        OrderStatus.checkTransition(order.getStatus(), OrderStatus.COMMENTED);

        boolean updated = lambdaUpdate()
                .set(Order::getStatus, OrderStatus.COMMENTED.getValue())
                .set(Order::getCommentTime, LocalDateTime.now())
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, order.getStatus())
                .update();
        if (!updated) {
            throw new BizIllegalException("订单状态已变化，标记评价失败");
        }
    }

    private Order getOrThrow(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        return order;
    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}
