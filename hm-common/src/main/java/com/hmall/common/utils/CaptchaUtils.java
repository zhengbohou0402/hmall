package com.hmall.common.utils;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;

/**
 * 图形验证码生成，轻量实现（Hutool），用于保护短信发送接口不被刷
 */
public class CaptchaUtils {

    public static LineCaptcha generate() {
        return CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
    }
}
