package com.gao.locallife.service;

import com.gao.locallife.dto.LoginFormDTO;
import com.gao.locallife.dto.UserDTO;

/** 用户与登录相关业务。 */
public interface UserService {

    /**
     * 发送验证码。
     *
     * @return 开发期直接返回验证码方便调试（生产必须去掉）
     */
    String sendCode(String phone);

    /**
     * 登录：校验验证码 -> 查/建用户 -> 生成 token 写入 Redis。
     *
     * @return token
     */
    String login(LoginFormDTO form);

    /** 登出：删除 Redis 里的 token。 */
    void logout(String token);

    /** 当前登录用户（从 ThreadLocal 取）。 */
    UserDTO currentUser();
}
