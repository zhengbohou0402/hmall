package com.hmall.common.config;

import com.hmall.common.utils.sms.LogSmsSender;
import com.hmall.common.utils.sms.SmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsConfig {

    @Bean
    @ConditionalOnMissingBean
    public SmsSender smsSender() {
        return new LogSmsSender();
    }
}
