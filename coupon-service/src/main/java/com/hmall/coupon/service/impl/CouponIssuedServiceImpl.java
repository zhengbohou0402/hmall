package com.hmall.coupon.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.coupon.domain.po.CouponIssued;
import com.hmall.coupon.mapper.CouponIssuedMapper;
import com.hmall.coupon.service.ICouponIssuedService;
import org.springframework.stereotype.Service;

@Service
public class CouponIssuedServiceImpl extends ServiceImpl<CouponIssuedMapper, CouponIssued> implements ICouponIssuedService {
}
