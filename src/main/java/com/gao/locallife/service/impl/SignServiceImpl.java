package com.gao.locallife.service.impl;

import com.gao.locallife.common.BusinessException;
import com.gao.locallife.common.RedisConstants;
import com.gao.locallife.common.UserHolder;
import com.gao.locallife.service.SignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 签到：key = sign:{userId}:{yyyyMM}，用 Bitmap 存每天的签到状态。
 *
 * <pre>
 *   offset = 日期 - 1   （1 号存在第 0 位）
 *   一个月 31 天 = 4 个字节
 * </pre>
 *
 * <p>为什么不存 MySQL：一个月 30 行、每行几十字节，一年就是几百行；
 * Bitmap 一个月只要 4 字节，且 BITCOUNT/BITFIELD 一条命令就能统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignServiceImpl implements SignService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    @Override
    public boolean sign() {
        Long userId = currentUserId();
        LocalDate now = LocalDate.now();
        String key = key(userId, now);
        int day = now.getDayOfMonth();

        // setBit 返回旧值：true 表示今天之前已经签过
        Boolean old = stringRedisTemplate.opsForValue().setBit(key, day - 1, true);
        if (old == null || !old) {
            // 保留 2 个月，避免 key 无限堆积
            stringRedisTemplate.expire(key, 62, TimeUnit.DAYS);
            log.debug("用户 {} 第 {} 天签到", userId, day);
            return true;
        }
        return false;
    }

    @Override
    public long countThisMonth() {
        Long userId = currentUserId();
        String key = key(userId, LocalDate.now());
        Long count = stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().bitCount(key.getBytes(StandardCharsets.UTF_8), 0, -1));
        return count == null ? 0 : count;
    }

    @Override
    public long continuousDays() {
        Long userId = currentUserId();
        LocalDate now = LocalDate.now();
        String key = key(userId, now);
        int day = now.getDayOfMonth();

        // 一次取回「从 1 号到今天」这一段的位：GET u{day} 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0));

        if (result == null || result.isEmpty() || result.get(0) == null) {
            return 0;
        }
        long num = result.get(0);
        long count = 0;
        // 从最低位（今天）往前走，遇到 0 就停
        while (num != 0) {
            if ((num & 1) == 0) {
                break;
            }
            count++;
            num >>= 1;
        }
        return count;
    }

    private String key(Long userId, LocalDate date) {
        return RedisConstants.SIGN_KEY + userId + ":" + date.format(MONTH);
    }

    private Long currentUserId() {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        return userId;
    }
}
