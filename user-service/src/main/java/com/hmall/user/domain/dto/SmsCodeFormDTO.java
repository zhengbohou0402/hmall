package com.hmall.user.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "发送短信验证码请求，需先通过图形验证码校验")
public class SmsCodeFormDTO {
    @Schema(description = "手机号", required = true)
    @NotNull(message = "手机号不能为空")
    private String phone;
    @Schema(description = "图形验证码id", required = true)
    @NotNull(message = "图形验证码id不能为空")
    private String captchaId;
    @Schema(description = "图形验证码", required = true)
    @NotNull(message = "图形验证码不能为空")
    private String captchaCode;
}
