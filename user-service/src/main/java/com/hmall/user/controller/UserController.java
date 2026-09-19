package com.hmall.user.controller;

import com.hmall.user.domain.dto.LoginFormDTO;
import com.hmall.user.domain.dto.SmsCodeFormDTO;
import com.hmall.user.domain.dto.SmsLoginFormDTO;
import com.hmall.user.domain.vo.CaptchaVO;
import com.hmall.user.domain.vo.UserLoginVO;
import com.hmall.user.service.IUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户相关接口")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @Operation(summary = "用户登录接口")
    @PostMapping("login")
    public UserLoginVO login(@RequestBody @Validated LoginFormDTO loginFormDTO){
        return userService.login(loginFormDTO);
    }

    @Operation(summary = "获取图形验证码")
    @GetMapping("captcha")
    public CaptchaVO captcha() {
        return userService.generateCaptcha();
    }

    @Operation(summary = "发送短信验证码，需先通过图形验证码校验")
    @PostMapping("code")
    public void sendSmsCode(@RequestBody @Validated SmsCodeFormDTO smsCodeFormDTO) {
        userService.sendSmsCode(smsCodeFormDTO);
    }

    @Operation(summary = "手机号验证码登录（手机号不存在时自动注册）")
    @PostMapping("login/sms")
    public UserLoginVO loginBySms(@RequestBody @Validated SmsLoginFormDTO smsLoginFormDTO) {
        return userService.loginByPhone(smsLoginFormDTO);
    }

    @Operation(summary = "扣减余额")
    @Parameters({
            @Parameter(name = "pw", description = "支付密码"),
            @Parameter(name = "amount", description = "支付金额")
    })
    @PutMapping("/money/deduct")
    public void deductMoney(@RequestParam("pw") String pw,@RequestParam("amount") Integer amount){
        userService.deductMoney(pw, amount);
    }
}

