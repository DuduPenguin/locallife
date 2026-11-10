package com.gao.locallife.controller;

import com.gao.locallife.common.Result;
import com.gao.locallife.common.SystemConstants;
import com.gao.locallife.dto.LoginFormDTO;
import com.gao.locallife.dto.UserDTO;
import com.gao.locallife.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户与登录接口。 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 发验证码。
     *
     * <p>开发期把验证码直接返回，方便测试；生产必须去掉。
     */
    @PostMapping("/code")
    public Result<String> sendCode(@RequestParam("phone") String phone) {
        return Result.ok(userService.sendCode(phone));
    }

    /** 登录，返回 token。 */
    @PostMapping("/login")
    public Result<String> login(@RequestBody @Valid LoginFormDTO form) {
        return Result.ok(userService.login(form));
    }

    /** 登出：删除 Redis 里的 token。 */
    @PostMapping("/logout")
    public Result<Void> logout(
            @RequestHeader(value = SystemConstants.TOKEN_HEADER, required = false) String token) {
        userService.logout(token);
        return Result.ok();
    }

    /** 当前登录用户（需要带 token）。 */
    @GetMapping("/me")
    public Result<UserDTO> me() {
        return Result.ok(userService.currentUser());
    }
}
