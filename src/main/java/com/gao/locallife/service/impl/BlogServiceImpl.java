package com.gao.locallife.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gao.locallife.common.BusinessException;
import com.gao.locallife.common.RedisConstants;
import com.gao.locallife.common.UserHolder;
import com.gao.locallife.dto.FeedResult;
import com.gao.locallife.entity.Blog;
import com.gao.locallife.mapper.BlogMapper;
import com.gao.locallife.service.BlogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 点赞用 ZSet（member = userId, score = 时间戳）：既能判断是否点过赞，又能按时间取人。
 *
 * <p>Feed 用推模式：发布时写给所有粉丝（写扩散），读的时候直接取自己的 ZSet。
 * 适合粉丝量不大的场景；大 V 要改成推拉结合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogServiceImpl implements BlogService {

    private final BlogMapper blogMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** Feed 最多保留 1000 条，防内存无限增长 */
    private static final int FEED_MAX_SIZE = 1000;
    /** 单页返回条数 */
    private static final int FEED_PAGE_SIZE = 2;

    @Override
    public Long publish(Blog blog) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        blog.setId(null);
        blog.setUserId(userId);
        blog.setLiked(0);
        blog.setComments(0);
        blogMapper.insert(blog);

        // 推模式：把我关注的粉丝的 feed 里都塞一条
        Set<String> fans = stringRedisTemplate.opsForSet()
                .members(RedisConstants.FANS_KEY + userId);
        if (fans != null) {
            long now = System.currentTimeMillis();
            for (String fanId : fans) {
                String feedKey = RedisConstants.FEED_KEY + fanId;
                stringRedisTemplate.opsForZSet().add(feedKey, String.valueOf(blog.getId()), now);
                // 只保留最新 1000 条（按 score 升序，删掉排名靠前的多余部分）
                stringRedisTemplate.opsForZSet().removeRange(feedKey, 0, -(FEED_MAX_SIZE + 1));
            }
            log.debug("笔记 {} 已推送给 {} 个粉丝", blog.getId(), fans.size());
        }
        return blog.getId();
    }

    @Override
    public boolean toggleLike(Long blogId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        String likeKey = RedisConstants.LIKE_BLOG_KEY + blogId;
        String member = String.valueOf(userId);

        Double score = stringRedisTemplate.opsForZSet().score(likeKey, member);
        if (score == null) {
            // 没点过 → 点赞
            stringRedisTemplate.opsForZSet().add(likeKey, member, System.currentTimeMillis());
            blogMapper.update(null, new LambdaUpdateWrapper<Blog>()
                    .eq(Blog::getId, blogId).setSql("liked = liked + 1"));
            return true;
        }
        // 点过 → 取消
        stringRedisTemplate.opsForZSet().remove(likeKey, member);
        blogMapper.update(null, new LambdaUpdateWrapper<Blog>()
                .eq(Blog::getId, blogId).setSql("liked = liked - 1"));
        return false;
    }

    @Override
    public List<Long> likeTop(Long blogId, Integer current) {
        String likeKey = RedisConstants.LIKE_BLOG_KEY + blogId;
        int page = current == null ? 1 : current;
        long start = (long) (page - 1) * 5;
        Set<String> ids = stringRedisTemplate.opsForZSet()
                .reverseRange(likeKey, start, start + 4);
        List<Long> result = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                result.add(Long.valueOf(id));
            }
        }
        return result;
    }

    /**
     * Feed 读取：时间戳游标 + 同分偏移。
     *
     * <p>`lastTime` 为上一页最后一条的 score，`offset` 是"同分已消费条数"，
     * 用来处理"同一毫秒发了多条"的情况。
     */
    @Override
    public FeedResult feed(Long lastTime, Integer offset) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        String feedKey = RedisConstants.FEED_KEY + userId;
        long max = lastTime == null ? System.currentTimeMillis() : lastTime;
        int skip = offset == null ? 0 : offset;

        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(feedKey, 0, max, skip, FEED_PAGE_SIZE);

        if (tuples == null || tuples.isEmpty()) {
            return new FeedResult(List.of(), max, 0);
        }

        List<Long> blogIds = new ArrayList<>();
        long minTime = max;
        int sameScoreCount = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            if (StrUtil.isNotBlank(value)) {
                blogIds.add(Long.valueOf(value));
            }
            long time = tuple.getScore() == null ? 0 : tuple.getScore().longValue();
            if (time == minTime) {
                sameScoreCount++;
            } else {
                minTime = time;
                sameScoreCount = 1;
            }
        }

        List<Blog> blogs = blogIds.isEmpty() ? List.of() : blogMapper.selectBatchIds(blogIds);
        // selectBatchIds 不保证顺序，按 blogIds 顺序重排
        List<Blog> ordered = new ArrayList<>();
        for (Long id : blogIds) {
            blogs.stream().filter(b -> b.getId().equals(id)).findFirst().ifPresent(ordered::add);
        }
        return new FeedResult(ordered, minTime, sameScoreCount);
    }

    @Override
    public List<Blog> byShop(Long shopId, Integer current) {
        Page<Blog> page = blogMapper.selectPage(
                new Page<>(current == null ? 1 : current, 5),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Blog>()
                        .eq(Blog::getShopId, shopId)
                        .orderByDesc(Blog::getCreateTime));
        return page.getRecords();
    }
}
