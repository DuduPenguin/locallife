package com.gao.locallife.controller;

import com.gao.locallife.common.Result;
import com.gao.locallife.service.SignService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 签到。 */
@RestController
@RequiredArgsConstructor
public class SignController {

    private final SignService signService;

    /** 今日签到：true 表示本次签到成功，false 表示今天已签过。 */
    @PostMapping("/sign")
    public Result<Boolean> sign() {
        return Result.ok(signService.sign());
    }

    /** 本月已签到天数。 */
    @GetMapping("/sign/count")
    public Result<Long> count() {
        return Result.ok(signService.countThisMonth());
    }

    /** 连续签到天数。 */
    @GetMapping("/sign/continuous")
    public Result<Long> continuous() {
        return Result.ok(signService.continuousDays());
    }
}
