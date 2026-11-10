package com.gao.locallife.controller;

import com.gao.locallife.common.Result;
import com.gao.locallife.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 关注关系。 */
@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /** 关注某人。 */
    @PostMapping("/follow/{id}")
    public Result<Void> follow(@PathVariable("id") Long followUserId) {
        followService.follow(followUserId);
        return Result.ok();
    }

    /** 取关。 */
    @DeleteMapping("/follow/{id}")
    public Result<Void> unfollow(@PathVariable("id") Long followUserId) {
        followService.unfollow(followUserId);
        return Result.ok();
    }

    /** 是否已关注。 */
    @GetMapping("/follow/or/not/{id}")
    public Result<Boolean> isFollowed(@PathVariable("id") Long followUserId) {
        return Result.ok(followService.isFollowed(followUserId));
    }

    /** 我和 TA 的共同关注（SINTER）。 */
    @GetMapping("/follow/common/{id}")
    public Result<List<Long>> common(@PathVariable("id") Long otherUserId) {
        return Result.ok(followService.commonFollows(otherUserId));
    }
}
