package com.hmall.coupon.service.impl;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.dto.CouponConsumeDTO;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.RedisStockUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.coupon.constants.CouponConstants;
import com.hmall.coupon.domain.dto.CouponTemplateFormDTO;
import com.hmall.coupon.domain.po.CouponIssued;
import com.hmall.coupon.domain.po.CouponTemplate;
import com.hmall.coupon.mapper.CouponTemplateMapper;
import com.hmall.coupon.service.ICouponIssuedService;
import com.hmall.coupon.service.ICouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponTemplateMapper, CouponTemplate> implements ICouponService {

    private final Snowflake snowflake;
    private final RedisStockUtils redisStockUtils;
    private final ICouponIssuedService issuedService;

    @Override
    public Long createTemplate(CouponTemplateFormDTO formDTO) {
        CouponTemplate template = new CouponTemplate()
                .setId(snowflake.nextId())
                .setName(formDTO.getName())
                .setDiscountType(formDTO.getDiscountType())
                .setDiscountValue(formDTO.getDiscountValue())
                .setThresholdAmount(formDTO.getThresholdAmount() == null ? 0 : formDTO.getThresholdAmount())
                .setTotalQuantity(formDTO.getTotalQuantity())
                .setIssuedQuantity(0)
                .setPerUserLimit(formDTO.getPerUserLimit() == null ? 1 : formDTO.getPerUserLimit())
                .setValidStart(formDTO.getValidStart())
                .setValidEnd(formDTO.getValidEnd())
                .setStatus(1);
        save(template);
        // 和秒杀一样，发放总量预热进 Redis，领券只碰 Redis
        redisStockUtils.initStock(CouponConstants.stockKey(template.getId()), formDTO.getTotalQuantity());
        return template.getId();
    }

    @Override
    public List<CouponTemplate> listAvailable() {
        return lambdaQuery().eq(CouponTemplate::getStatus, 1).list();
    }

    @Override
    public String claim(Long templateId) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        CouponTemplate template = getById(templateId);
        if (template == null || template.getStatus() != 1) {
            throw new BadRequestException("优惠券不存在或已下架");
        }

        // 复用秒杀那套 Lua：限量扣减 + 每人限领去重，一次原子完成
        long result = redisStockUtils.deductOncePerUser(
                CouponConstants.stockKey(templateId),
                CouponConstants.userSetKey(templateId),
                1, userId);
        if (result == -2L) {
            throw new BizIllegalException("您已领取过该优惠券");
        }
        if (result == 0L) {
            throw new BizIllegalException("优惠券已领完");
        }
        if (result == -1L) {
            throw new BizIllegalException("优惠券未开放领取");
        }

        String code = RandomUtil.randomStringUpper(12);
        CouponIssued issued = new CouponIssued()
                .setId(snowflake.nextId())
                .setTemplateId(templateId)
                .setUserId(userId)
                .setCouponCode(code)
                .setStatus(1)
                .setIssueTime(LocalDateTime.now());
        try {
            issuedService.save(issued);
        } catch (Exception e) {
            // 落库失败要把 Redis 预扣的名额还回去，否则这张券会凭空消失
            redisStockUtils.restore(CouponConstants.stockKey(templateId),
                    CouponConstants.userSetKey(templateId), 1, userId);
            throw e;
        }
        lambdaUpdate()
                .setSql("issued_quantity = issued_quantity + 1")
                .eq(CouponTemplate::getId, templateId)
                .update();
        return code;
    }

    @Override
    public List<CouponIssued> myCoupons() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("请先登录");
        }
        return issuedService.lambdaQuery().eq(CouponIssued::getUserId, userId).list();
    }

    @Override
    public Integer validateAndConsume(CouponConsumeDTO dto) {
        CouponIssued issued = issuedService.lambdaQuery()
                .eq(CouponIssued::getCouponCode, dto.getCouponCode())
                .one();
        if (issued == null) {
            throw new BadRequestException("优惠券不存在");
        }
        if (!issued.getUserId().equals(dto.getUserId())) {
            throw new BizIllegalException("该优惠券不属于当前用户");
        }
        if (issued.getStatus() != 1) {
            throw new BizIllegalException("优惠券已使用或已过期");
        }

        CouponTemplate template = getById(issued.getTemplateId());
        LocalDateTime now = LocalDateTime.now();
        if (template.getValidStart() != null && now.isBefore(template.getValidStart())) {
            throw new BizIllegalException("优惠券未到可用时间");
        }
        if (template.getValidEnd() != null && now.isAfter(template.getValidEnd())) {
            throw new BizIllegalException("优惠券已过期");
        }
        if (dto.getOrderAmount() < template.getThresholdAmount()) {
            throw new BizIllegalException("订单金额未达到使用门槛");
        }

        int discount = calcDiscount(template, dto.getOrderAmount());

        // 状态守卫，防止同一张券被并发重复核销
        boolean used = issuedService.lambdaUpdate()
                .set(CouponIssued::getStatus, 2)
                .set(CouponIssued::getUseTime, now)
                .set(CouponIssued::getOrderId, dto.getOrderId())
                .eq(CouponIssued::getId, issued.getId())
                .eq(CouponIssued::getStatus, 1)
                .update();
        if (!used) {
            throw new BizIllegalException("优惠券已被使用");
        }
        return discount;
    }

    private int calcDiscount(CouponTemplate template, int orderAmount) {
        if (template.getDiscountType() == 1) {
            // 满减：优惠额不能超过订单金额本身
            return Math.min(template.getDiscountValue(), orderAmount);
        }
        // 折扣：discountValue=85 表示 8.5 折，优惠额 = 订单金额 * (100-85) / 100
        return orderAmount * (100 - template.getDiscountValue()) / 100;
    }
}
