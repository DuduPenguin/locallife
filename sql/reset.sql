-- ============================================================================
-- 重置秒杀数据（本地压测 / 联调用）
--
-- 用法：
--   mysql ... --database=locallife < sql/reset.sql
--
-- ⚠️ 执行后必须【重启应用】：
--    StockPreheatRunner 会把 DB 库存重新写进 Redis、
--    清空已购集合、并记录初始库存（对账基准）。
--    只改 DB 不重启，Redis 里还是 0，下单会一直提示"库存不足"。
-- ============================================================================

UPDATE tb_seckill_voucher SET stock = 100 WHERE voucher_id = 2;
DELETE FROM tb_voucher_order WHERE voucher_id = 2;

-- 验证：stock 应该是 100，orders 应该是 0
SELECT stock FROM tb_seckill_voucher WHERE voucher_id = 2;
SELECT COUNT(*) AS orders FROM tb_voucher_order WHERE voucher_id = 2;
