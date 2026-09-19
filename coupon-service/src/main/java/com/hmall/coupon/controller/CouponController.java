package com.hmall.coupon.controller;

import com.hmall.api.dto.CouponConsumeDTO;
import com.hmall.coupon.domain.dto.CouponTemplateFormDTO;
import com.hmall.coupon.domain.po.CouponIssued;
import com.hmall.coupon.domain.po.CouponTemplate;
import com.hmall.coupon.service.ICouponService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "优惠券相关接口")
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final ICouponService couponService;

    // TODO Phase4 RBAC：创建券模板应限制为 ADMIN 角色，当前仅要求登录
    @Operation(summary = "创建优惠券模板")
    @PostMapping("/templates")
    public Long createTemplate(@RequestBody @Validated CouponTemplateFormDTO formDTO) {
        return couponService.createTemplate(formDTO);
    }

    @Operation(summary = "查询可领取的优惠券")
    @GetMapping("/templates")
    public List<CouponTemplate> listAvailable() {
        return couponService.listAvailable();
    }

    @Operation(summary = "领取优惠券，返回优惠码")
    @PostMapping("/claim/{templateId}")
    public String claim(@PathVariable("templateId") Long templateId) {
        return couponService.claim(templateId);
    }

    @Operation(summary = "查询我的优惠券")
    @GetMapping("/my")
    public List<CouponIssued> myCoupons() {
        return couponService.myCoupons();
    }

    @Operation(summary = "校验并核销优惠券，供trade-service下单时调用，返回优惠金额(分)")
    @PostMapping("/consume")
    public Integer validateAndConsume(@RequestBody CouponConsumeDTO dto) {
        return couponService.validateAndConsume(dto);
    }
}
