-- 库存回补（幂等）
--
-- KEYS[1] = seckill:stock:{voucherId}
-- KEYS[2] = seckill:order:{voucherId}
-- ARGV[1] = userId
--
-- 返回 1 表示真的回补了，0 表示这个用户本来就不在已购集合里（重复消息/已回补过）
--
-- 为什么要判 SISMEMBER：取消操作可能被重复触发（延迟消息重投 + 手动取消），
-- 不判断就会把库存加多，反而变成超卖。

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 0 then
    return 0
end
redis.call('INCR', KEYS[1])
redis.call('SREM', KEYS[2], ARGV[1])
return 1
