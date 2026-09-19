package com.hmall.api.config;

import com.hmall.api.client.fallback.CouponClientFallbackFactory;
import com.hmall.api.client.fallback.ItemClientFallbackFactory;
import com.hmall.common.utils.UserContext;
import feign.Logger;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

public class DefaultFeignConfig {
    @Bean
    public Logger.Level feignLoggerLevel(){
        return Logger.Level.FULL;
    }

    @Bean
    public RequestInterceptor userInfoRequestInterceptor(){
        return template -> {
            Long userId = UserContext.getUser();
            if(userId == null){
                return;
            }
            // 必须与下游 UserInfoInterceptor 读取的头名一致（"user-info"），
            // 原来写的是 "user_id"，两边对不上，导致跨 Feign 调用时登录态根本传不过去
            template.header("user-info", String.valueOf(userId));
        };
    }

    @Bean
    public ItemClientFallbackFactory itemClientFallbackFactory(){
        return new ItemClientFallbackFactory();
    }

    @Bean
    public CouponClientFallbackFactory couponClientFallbackFactory(){
        return new CouponClientFallbackFactory();
    }

    /**
     * 让下游的业务异常消息能透传上来，而不是一律退化成"服务器内部异常"
     */
    @Bean
    public ErrorDecoder bizErrorDecoder(){
        return new BizErrorDecoder();
    }
}
