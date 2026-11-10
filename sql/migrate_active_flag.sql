-- ============================================================================
-- 修复：一人一单校验把「已取消」订单也算进去
--
-- 现象：用户下单后 30 分钟未支付被取消，之后再想抢同一张券，
--       消费端查 count(user_id, voucher_id) 得到 1，直接跳过落库，
--       结果 Redis 扣了库存、数据库没有订单 —— 少卖一件。
--
-- 根因：唯一索引 uk_user_voucher(user_id, voucher_id) 与"是否有效"无关，
--       已取消的订单把位置占住了。
--
-- 解法：加一个可空的有效标记 active_flag：
--       有效订单 = 1，取消时置 NULL。
--       MySQL 的唯一索引对 NULL 不去重，所以：
--         · 同一用户同一张券，只能有一条 active_flag=1（唯一索引挡住）
--         · 可以有任意多条 active_flag=NULL（已取消的历史单）
--
-- 这是 MySQL 实现"部分唯一索引"的常用办法（不支持 WHERE 条件索引）。
-- ============================================================================

ALTER TABLE tb_voucher_order
    ADD COLUMN active_flag TINYINT NULL DEFAULT 1 COMMENT '有效标记：1 有效，NULL 已取消（唯一索引对 NULL 不去重）';

-- 历史数据：已取消的置空
UPDATE tb_voucher_order SET active_flag = NULL WHERE status = 4;

-- 换掉旧索引：旧的只约束 (user_id, voucher_id)，会挡住重新下单
ALTER TABLE tb_voucher_order DROP INDEX uk_user_voucher;
ALTER TABLE tb_voucher_order
    ADD UNIQUE KEY uk_user_voucher_active (user_id, voucher_id, active_flag);

-- 校验
SELECT COUNT(*) AS total, SUM(active_flag = 1) AS active FROM tb_voucher_order;
