package com.hmall.common.aspect;

import com.hmall.common.annotation.RequireRole;
import com.hmall.common.exception.ForbiddenException;
import com.hmall.common.exception.UnauthorizedException;
import com.hmall.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.JoinPoint;

import java.util.Arrays;

/**
 * 校验 @RequireRole 标注的接口。角色来自网关透传的 user-roles 头（见 UserInfoInterceptor）。
 * 未登录返回 401，登录但角色不足返回 403，两者要区分开。
 */
@Slf4j
@Aspect
public class RoleCheckAspect {

    @Before("@annotation(requireRole)")
    public void checkRole(JoinPoint joinPoint, RequireRole requireRole) {
        if (UserContext.getUser() == null) {
            throw new UnauthorizedException("未登录");
        }
        boolean allowed = Arrays.stream(requireRole.value()).anyMatch(UserContext::hasRole);
        if (!allowed) {
            String method = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
            log.warn("用户{}访问{}被拒绝，需要角色{}，当前角色{}",
                    UserContext.getUser(), method,
                    Arrays.toString(requireRole.value()), UserContext.getRoles());
            throw new ForbiddenException("权限不足，需要角色：" + String.join("/", requireRole.value()));
        }
    }
}
