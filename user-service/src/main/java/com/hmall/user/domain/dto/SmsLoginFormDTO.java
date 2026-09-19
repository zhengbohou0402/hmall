package com.hmall.user.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
@Schema(description = "手机号验证码登录表单")
public class SmsLoginFormDTO {
    @Schema(description = "手机号", required = true)
    @NotNull(message = "手机号不能为空")
    private String phone;
    @Schema(description = "短信验证码", required = true)
    @NotNull(message = "验证码不能为空")
    private String code;
}
