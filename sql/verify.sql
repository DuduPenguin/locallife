-- ============================================================================
-- M1 验收脚本：逐条执行，对照期望结果
-- ============================================================================

-- 1. 表是否都建了（期望 9 行）
SELECT table_name, table_comment
FROM information_schema.tables
WHERE table_schema = 'locallife'
ORDER BY table_name;

-- 2. 数据条数（期望：5 / 2 / 2 / 10 / 3 / 2 / 1 / 0 / 1）
SELECT 'tb_shop_type'       AS t, COUNT(*) AS c FROM tb_shop_type
UNION ALL SELECT 'tb_user',            COUNT(*) FROM tb_user
UNION ALL SELECT 'tb_user_info',       COUNT(*) FROM tb_user_info
UNION ALL SELECT 'tb_shop',            COUNT(*) FROM tb_shop
UNION ALL SELECT 'tb_blog',            COUNT(*) FROM tb_blog
UNION ALL SELECT 'tb_voucher',         COUNT(*) FROM tb_voucher
UNION ALL SELECT 'tb_seckill_voucher', COUNT(*) FROM tb_seckill_voucher
UNION ALL SELECT 'tb_voucher_order',   COUNT(*) FROM tb_voucher_order
UNION ALL SELECT 'tb_follow',          COUNT(*) FROM tb_follow;

-- 3. 秒杀券库存（期望 stock = 100）
SELECT voucher_id, stock, begin_time, end_time
FROM tb_seckill_voucher;

-- 4. 关键索引是否建好（期望看到 uk_phone、uk_user_voucher、uk_user_follow）
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS cols, non_unique
FROM information_schema.statistics
WHERE table_schema = 'locallife'
  AND index_name LIKE 'uk_%'
GROUP BY table_name, index_name, non_unique;

-- 5. 唯一索引是否真的生效（第二条会报 Duplicate entry，说明约束正确）
INSERT INTO tb_follow (user_id, follow_user_id) VALUES (1, 2);   -- 期望：Duplicate entry '1-2'
