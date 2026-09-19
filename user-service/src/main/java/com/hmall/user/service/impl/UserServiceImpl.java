package com.hmall.user.service.impl;

import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.exception.ForbiddenException;
import com.hmall.common.utils.CaptchaUtils;
import com.hmall.common.utils.UserContext;
import com.hmall.common.utils.sms.SmsSender;
import com.hmall.user.config.JwtProperties;
import com.hmall.user.domain.dto.LoginFormDTO;
import com.hmall.user.domain.dto.SmsCodeFormDTO;
import com.hmall.user.domain.dto.SmsLoginFormDTO;
import com.hmall.user.domain.po.User;
import com.hmall.user.domain.vo.CaptchaVO;
import com.hmall.user.domain.vo.UserLoginVO;
import com.hmall.user.enums.UserStatus;
import com.hmall.user.mapper.RoleMapper;
import com.hmall.user.mapper.UserMapper;
import com.hmall.user.service.IUserService;
import com.hmall.user.utils.JwtTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private static final String CAPTCHA_KEY_PREFIX = "captcha:";
    private static final String SMS_CODE_KEY_PREFIX = "sms:code:";
    private static final String SMS_COOLDOWN_KEY_PREFIX = "sms:cooldown:";
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final Duration SMS_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SMS_COOLDOWN_TTL = Duration.ofSeconds(60);

    private final PasswordEncoder passwordEncoder;

    private final JwtTool jwtTool;

    private final JwtProperties jwtProperties;

    private final StringRedisTemplate redisTemplate;

    private final SmsSender smsSender;

    private final RoleMapper roleMapper;

    @Override
    public UserLoginVO login(LoginFormDTO loginDTO) {
        // 1.数据校验
        String username = loginDTO.getUsername();
        String password = loginDTO.getPassword();
        // 2.根据用户名或手机号查询
        User user = lambdaQuery().eq(User::getUsername, username).one();
        if (user == null) {
            throw new BadRequestException("用户名或密码错误");
        }
        // 3.校验是否禁用
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new ForbiddenException("用户被冻结");
        }
        // 4.校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("用户名或密码错误");
        }
        // 5.生成TOKEN，把角色一并写进 payload
        String token = jwtTool.createToken(user.getId(), jwtProperties.getTokenTTL(),
                roleMapper.selectRoleNamesByUserId(user.getId()));
        // 6.封装VO返回
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setBalance(user.getBalance());
        vo.setToken(token);
        return vo;
    }

    @Override
    public void deductMoney(String pw, Integer totalFee) {
        log.info("开始扣款");
        // 1.校验密码
        User user = getById(UserContext.getUser());
        if(user == null || !passwordEncoder.matches(pw, user.getPassword())){
            // 密码错误
            throw new BizIllegalException("用户密码错误");
        }

        // 2.尝试扣款
        try {
            baseMapper.updateMoney(UserContext.getUser(), totalFee);
        } catch (Exception e) {
            throw new RuntimeException("扣款失败，可能是余额不足！", e);
        }
        log.info("扣款成功");
    }

    @Override
    public CaptchaVO generateCaptcha() {
        LineCaptcha captcha = CaptchaUtils.generate();
        String captchaId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, captcha.getCode(), CAPTCHA_TTL);
        return new CaptchaVO(captchaId, captcha.getImageBase64());
    }

    @Override
    public void sendSmsCode(SmsCodeFormDTO dto) {
        // 1.校验图形验证码，一次性使用，无论对错都立即失效
        String captchaKey = CAPTCHA_KEY_PREFIX + dto.getCaptchaId();
        String expectedCaptcha = redisTemplate.opsForValue().get(captchaKey);
        redisTemplate.delete(captchaKey);
        if (expectedCaptcha == null || !expectedCaptcha.equalsIgnoreCase(dto.getCaptchaCode())) {
            throw new BadRequestException("图形验证码错误或已过期");
        }
        // 2.发送冷却限制，防止同一手机号被刷
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(SMS_COOLDOWN_KEY_PREFIX + dto.getPhone(), "1", SMS_COOLDOWN_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            throw new BizIllegalException("发送过于频繁，请稍后再试");
        }
        // 3.生成并保存短信验证码，mock发送（只记日志）
        String code = RandomUtil.randomNumbers(6);
        redisTemplate.opsForValue().set(SMS_CODE_KEY_PREFIX + dto.getPhone(), code, SMS_CODE_TTL);
        smsSender.send(dto.getPhone(), code);
    }

    @Override
    public UserLoginVO loginByPhone(SmsLoginFormDTO dto) {
        // 1.校验短信验证码，一次性使用
        String codeKey = SMS_CODE_KEY_PREFIX + dto.getPhone();
        String expectedCode = redisTemplate.opsForValue().get(codeKey);
        redisTemplate.delete(codeKey);
        if (expectedCode == null || !expectedCode.equals(dto.getCode())) {
            throw new BadRequestException("验证码错误或已过期");
        }
        // 2.按手机号查找用户，不存在则视为手机号一键注册
        User user = lambdaQuery().eq(User::getPhone, dto.getPhone()).one();
        if (user == null) {
            // create_time/update_time 在数据库里是 NOT NULL 且无默认值，User 也没有配置自动填充，必须显式赋值
            LocalDateTime now = LocalDateTime.now();
            user = new User()
                    .setUsername(dto.getPhone())
                    .setPassword(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .setPhone(dto.getPhone())
                    .setStatus(UserStatus.NORMAL)
                    .setBalance(0)
                    .setCreateTime(now)
                    .setUpdateTime(now);
            save(user);
        }
        if (user.getStatus() == UserStatus.FROZEN) {
            throw new ForbiddenException("用户被冻结");
        }
        // 3.签发token，与用户名密码登录使用同一套逻辑
        String token = jwtTool.createToken(user.getId(), jwtProperties.getTokenTTL());
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setBalance(user.getBalance());
        vo.setToken(token);
        return vo;
    }
}
