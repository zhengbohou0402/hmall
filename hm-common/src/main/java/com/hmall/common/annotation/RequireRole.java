package com.hmall.common.annotation;

import java.lang.annotation.*;

/**
 * 标记需要特定角色才能访问的接口。放在 hm-common 供所有服务复用，
 * 避免每个服务各写一遍鉴权逻辑。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /** 满足其中任意一个角色即可通过 */
    String[] value();
}
