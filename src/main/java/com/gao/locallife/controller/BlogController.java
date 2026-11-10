package com.gao.locallife.controller;

import com.gao.locallife.common.Result;
import com.gao.locallife.dto.FeedResult;
import com.gao.locallife.entity.Blog;
import com.gao.locallife.service.BlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 探店笔记：发布 / 点赞 / Feed。 */
@RestController
@RequiredArgsConstructor
public class BlogController {

    private final BlogService blogService;

    /** 发布笔记（会推送到所有粉丝的 Feed）。 */
    @PostMapping("/blog")
    public Result<Long> publish(@RequestBody Blog blog) {
        return Result.ok(blogService.publish(blog));
    }

    /** 点赞 / 取消点赞（同一个接口切换，幂等）。 */
    @PostMapping("/blog-like/{id}")
    public Result<Boolean> like(@PathVariable("id") Long blogId) {
        // true = 本次点赞，false = 本次取消
        return Result.ok(blogService.toggleLike(blogId));
    }

    /** 点赞排行：按点赞时间倒序取最近的用户 id。 */
    @GetMapping("/blog-like/{id}/top")
    public Result<List<Long>> likeTop(@PathVariable("id") Long blogId,
                                     @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return Result.ok(blogService.likeTop(blogId, current));
    }

    /**
     * 我的 Feed（时间戳游标）。
     *
     * <p>首次不传 lastTime/offset；之后用返回的 minTime/offset 继续翻。
     */
    @GetMapping("/feed")
    public Result<FeedResult> feed(@RequestParam(value = "lastTime", required = false) Long lastTime,
                                   @RequestParam(value = "offset", required = false) Integer offset) {
        return Result.ok(blogService.feed(lastTime, offset));
    }

    /** 某商户下的笔记（普通 DB 分页，用来和 Feed 的游标分页对比）。 */
    @GetMapping("/blog/of/shop/{shopId}")
    public Result<List<Blog>> byShop(@PathVariable("shopId") Long shopId,
                                     @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return Result.ok(blogService.byShop(shopId, current));
    }
}
