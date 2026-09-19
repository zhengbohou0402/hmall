package com.hmall.common.utils.sms;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认的 mock 短信发送实现：只记日志，不调用真实短信网关，方便本地学习环境直接看到验证码
 */
@Slf4j
public class LogSmsSender implements SmsSender {
    @Override
    public void send(String phone, String code) {
        log.info("【模拟短信】发送验证码到 {}：{}（5分钟内有效，测试环境仅记录日志）", phone, code);
    }
}
