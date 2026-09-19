package com.hmall.common.config;

import com.hmall.common.aspect.RoleCheckAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * 只在 Servlet 技术栈下装配，网关（WebFlux）不需要方法级鉴权
 */
@Configuration
@ConditionalOnClass(DispatcherServlet.class)
public class RbacConfig {

    @Bean
    @ConditionalOnMissingBean
    public RoleCheckAspect roleCheckAspect() {
        return new RoleCheckAspect();
    }
}
