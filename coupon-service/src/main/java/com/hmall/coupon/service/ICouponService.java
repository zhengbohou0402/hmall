package com.hmall.coupon.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmall.api.dto.CouponConsumeDTO;
import com.hmall.coupon.domain.dto.CouponTemplateFormDTO;
import com.hmall.coupon.domain.po.CouponIssued;
import com.hmall.coupon.domain.po.CouponTemplate;

import java.util.List;

public interface ICouponService extends IService<CouponTemplate> {

    Long createTemplate(CouponTemplateFormDTO formDTO);

    List<CouponTemplate> listAvailable();

    /**
     * 领券，返回优惠码
     */
    String claim(Long templateId);

    List<CouponIssued> myCoupons();

    /**
     * 校验并核销，返回优惠金额（分）
     */
    Integer validateAndConsume(CouponConsumeDTO dto);
}
