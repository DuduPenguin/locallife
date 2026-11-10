package com.gao.locallife.service.impl;

import com.gao.locallife.common.BusinessException;
import com.gao.locallife.common.RedisConstants;
import com.gao.locallife.common.UserHolder;
import com.gao.locallife.entity.Follow;
import com.gao.locallife.mapper.FollowMapper;
import com.gao.locallife.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 关注：DB 存权威关系，Redis 用两个 Set 加速查询。
 *
 * <pre>
 *   follow:follows:{userId}  我关注了谁
 *   follow:fans:{userId}     谁关注了我
 * </pre>
 *
 * 只存一份的话，查粉丝要遍历所有人 —— 空间换时间。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long followUserId) {
        Long userId = currentUserId();
        if (userId.equals(followUserId)) {
            throw new BusinessException(400, "不能关注自己");
        }
        if (isFollowed(followUserId)) {
            return;                       // 幂等
        }
        Follow follow = new Follow();
        follow.setUserId(userId);
        follow.setFollowUserId(followUserId);
        try {
            followMapper.insert(follow);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引冲突 = 关系已经存在（初始化数据 / 并发重复提交）
            // 这里不能报错，要当成"已关注"，并继续把 Redis 补齐（自愈）
            log.debug("关注关系已存在，补写 Redis userId={} followUserId={}", userId, followUserId);
        }

        // 不管 DB 是否新插入，都把 Redis 写一遍，保证两边最终一致
        stringRedisTemplate.opsForSet()
                .add(RedisConstants.FOLLOW_KEY + userId, String.valueOf(followUserId));
        stringRedisTemplate.opsForSet()
                .add(RedisConstants.FANS_KEY + followUserId, String.valueOf(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long followUserId) {
        Long userId = currentUserId();
        followMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId));

        stringRedisTemplate.opsForSet()
                .remove(RedisConstants.FOLLOW_KEY + userId, String.valueOf(followUserId));
        stringRedisTemplate.opsForSet()
                .remove(RedisConstants.FANS_KEY + followUserId, String.valueOf(userId));
    }

    @Override
    public boolean isFollowed(Long followUserId) {
        Long userId = currentUserId();
        Boolean member = stringRedisTemplate.opsForSet()
                .isMember(RedisConstants.FOLLOW_KEY + userId, String.valueOf(followUserId));
        return Boolean.TRUE.equals(member);
    }

    @Override
    public List<Long> commonFollows(Long otherUserId) {
        Long userId = currentUserId();
        Set<String> common = stringRedisTemplate.opsForSet().intersect(
                RedisConstants.FOLLOW_KEY + userId,
                RedisConstants.FOLLOW_KEY + otherUserId);
        List<Long> result = new ArrayList<>();
        if (common != null) {
            for (String id : common) {
                result.add(Long.valueOf(id));
            }
        }
        return result;
    }

    private Long currentUserId() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}
