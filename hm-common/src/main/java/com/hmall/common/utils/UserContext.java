package com.hmall.common.utils;

import java.util.Collections;
import java.util.Set;

public class UserContext {
    private static final ThreadLocal<Long> tl = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> roleTl = new ThreadLocal<>();

    /**
     * 保存当前登录用户信息到ThreadLocal
     * @param userId 用户id
     */
    public static void setUser(Long userId) {
        tl.set(userId);
    }

    /**
     * 获取当前登录用户信息
     * @return 用户id
     */
    public static Long getUser() {
        return tl.get();
    }

    /**
     * 保存网关透传下来的角色集合
     */
    public static void setRoles(Set<String> roles) {
        roleTl.set(roles);
    }

    public static Set<String> getRoles() {
        Set<String> roles = roleTl.get();
        return roles == null ? Collections.emptySet() : roles;
    }

    public static boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    /**
     * 移除当前登录用户信息
     */
    public static void removeUser(){
        tl.remove();
        roleTl.remove();
    }
}
