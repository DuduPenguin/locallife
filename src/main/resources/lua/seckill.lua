-- 秒杀原子脚本：判断库存 + 判断一人一单 + 扣减 + 记录，一次执行完
--
-- KEYS[1] = seckill:stock:{voucherId}   库存
-- KEYS[2] = seckill:order:{voucherId}   已购集合（Set，member = userId）
-- ARGV[1] = userId
--
-- 返回码：
--   0 抢购成功
--   1 库存不足
--   2 重复下单
--   3 库存未预热（key 不存在）
--
-- 注意三点：
--   1. GET 拿到的是字符串，必须 tonumber 才能比较，否则报
--      "attempt to compare string with number"
--   2. 所有校验都放在写入之前 —— Lua 不具备回滚能力
--   3. 集群模式下两个 key 必须在同一个 slot，所以业务代码里用了 hash tag {}

local stock = redis.call('GET', KEYS[1])
if stock == false then
    return 3
end
if tonumber(stock) <= 0 then
    return 1
end
if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return 2
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 0
