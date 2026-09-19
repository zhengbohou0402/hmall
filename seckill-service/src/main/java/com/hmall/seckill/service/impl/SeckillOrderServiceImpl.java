package com.hmall.seckill.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.seckill.domain.po.SeckillOrder;
import com.hmall.seckill.mapper.SeckillOrderMapper;
import com.hmall.seckill.service.ISeckillOrderService;
import org.springframework.stereotype.Service;

@Service
public class SeckillOrderServiceImpl extends ServiceImpl<SeckillOrderMapper, SeckillOrder> implements ISeckillOrderService {
}
