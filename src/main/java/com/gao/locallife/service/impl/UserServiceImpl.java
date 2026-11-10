package com.gao.locallife.service.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gao.locallife.common.BusinessException;
import com.gao.locallife.common.SystemConstants;
import com.gao.locallife.common.UserHolder;
import com.gao.locallife.dto.LoginFormDTO;
import com.gao.locallife.dto.UserDTO;
import com.gao.locallife.entity.User;
import com.gao.locallife.mapper.UserMapper;
import com.gao.locallife.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.gao.locallife.common.RedisConstants.LOGIN_CODE_KEY;
import static com.gao.locallife.common.RedisConstants.LOGIN_CODE_TTL;
import static com.gao.locallife.common.RedisConstants.LOGIN_TOKEN_KEY;
import static com.gao.locallife.common.RedisConstants.LOGIN_TOKEN_TTL;

/**
 * 登录实现：Redis Hash 存登录态 + 随机 token。
 *
 * <p>为什么不用 JWT：JWT 不能主动失效（改密码/踢人）、也不能滑动续期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 发验证码。开发期把码直接返回，方便不接短信就能测。 */
    @Override
    public String sendCode(String phone) {
        if (!Validator.isMobile(phone)) {
            throw new BusinessException(400, "手机号格式不正确");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue()
                .set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.SECONDS);
        log.debug("发送验证码 phone={} code={}", phone, code);
        // 生产环境：走短信服务，绝不返回
        return code;
    }

    /** 登录：校验码 -> 查/建用户 -> 写 Redis Hash -> 返回 token。 */
    @Override
    public String login(LoginFormDTO form) {
        String phone = form.getPhone();

        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(form.getCode())) {
            throw new BusinessException(400, "验证码错误或已过期");
        }
        // 一次性使用，登录成功立刻删除，防重放
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        boolean isNew = false;
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickName(SystemConstants.DEFAULT_NICKNAME_PREFIX + RandomUtil.randomString(6));
            user.setIcon(SystemConstants.DEFAULT_ICON);
            userMapper.insert(user);
            isNew = true;
        }

        String token = UUID.randomUUID().toString().replace("-", "");   // 32 位，去掉横线
        String key = LOGIN_TOKEN_KEY + token;

        Map<String, String> map = new HashMap<>(4);
        map.put("id", String.valueOf(user.getId()));
        map.put("nickName", StrUtil.blankToDefault(user.getNickName(), ""));
        map.put("icon", StrUtil.blankToDefault(user.getIcon(), ""));
        stringRedisTemplate.opsForHash().putAll(key, map);
        stringRedisTemplate.expire(key, LOGIN_TOKEN_TTL, TimeUnit.SECONDS);

        log.debug("登录成功 userId={} isNew={}", user.getId(), isNew);
        return token;
    }

    @Override
    public void logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(LOGIN_TOKEN_KEY + token);
        }
    }

    @Override
    public UserDTO currentUser() {
        UserDTO dto = UserHolder.getUser();
        if (dto == null) {
            throw new BusinessException(401, "请先登录");
        }
        return dto;
    }
}
