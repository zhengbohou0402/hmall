package com.hmall.common.utils.sms;

/**
 * 短信发送抽象，学习项目默认走 {@link LogSmsSender} 记日志，不接入真实短信网关；
 * 后续接入真实供应商（阿里云/腾讯云短信）时只需提供一个覆盖此接口的 Bean
 */
public interface SmsSender {
    void send(String phone, String code);
}
