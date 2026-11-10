-- ============================================================================
-- LocalLife 初始化脚本（M1）
--
-- 用法：
--   mysql -h127.0.0.1 -uroot -p123456 locallife < sql/init.sql
--   或在 IDEA Database / Navicat 里整段执行
--
-- 注意：本脚本会 DROP 后重建，仅用于开发环境！
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1. 用户
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_user;
CREATE TABLE tb_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone       VARCHAR(11)  NOT NULL                COMMENT '手机号',
    password    VARCHAR(128) DEFAULT ''              COMMENT '密码（本项目主体用验证码登录，此字段备用）',
    nick_name   VARCHAR(32)  NOT NULL DEFAULT ''     COMMENT '昵称',
    icon        VARCHAR(255) DEFAULT ''              COMMENT '头像',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';

-- ---------------------------------------------------------------------------
-- 2. 用户详情（与主表拆开：冷热分离，详情字段多且读得少）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_user_info;
CREATE TABLE tb_user_info (
    user_id     BIGINT      NOT NULL COMMENT '用户 id（与 tb_user.id 一致）',
    city        VARCHAR(64)  DEFAULT '' COMMENT '城市',
    introduce   VARCHAR(128) DEFAULT '' COMMENT '简介',
    gender      TINYINT      DEFAULT 0  COMMENT '性别 0未知 1男 2女',
    birthday    DATE         DEFAULT NULL,
    credits     INT          DEFAULT 0  COMMENT '积分',
    level       TINYINT      DEFAULT 1  COMMENT '等级',
    fans        INT          DEFAULT 0  COMMENT '粉丝数（冗余）',
    followee    INT          DEFAULT 0  COMMENT '关注数（冗余）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户详情表';

-- ---------------------------------------------------------------------------
-- 3. 商户分类
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_shop_type;
CREATE TABLE tb_shop_type (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32) NOT NULL COMMENT '类型名称',
    icon        VARCHAR(255) DEFAULT '' COMMENT '图标',
    sort        INT          DEFAULT 0  COMMENT '排序',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '商户分类表';

-- ---------------------------------------------------------------------------
-- 4. 商户
--    x / y 存经纬度，M7 会写入 Redis GEO
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_shop;
CREATE TABLE tb_shop (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '商户名称',
    type_id     BIGINT       NOT NULL COMMENT '分类 id',
    images      VARCHAR(1024) DEFAULT '' COMMENT '图片，逗号分隔',
    area        VARCHAR(64)  DEFAULT '' COMMENT '商圈',
    address     VARCHAR(255) DEFAULT '' COMMENT '地址',
    x           DOUBLE       DEFAULT NULL COMMENT '经度',
    y           DOUBLE       DEFAULT NULL COMMENT '纬度',
    avg_price   BIGINT       DEFAULT 0  COMMENT '人均消费（分）',
    sold        INT          DEFAULT 0  COMMENT '销量',
    comments    INT          DEFAULT 0  COMMENT '评论数',
    score       INT          DEFAULT 50 COMMENT '评分（0~50，除以 10 显示）',
    open_hours  VARCHAR(64)  DEFAULT '' COMMENT '营业时间',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_type_id (type_id),
    KEY idx_name (name),
    KEY idx_score (score)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '商户表';

-- ---------------------------------------------------------------------------
-- 5. 探店笔记
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_blog;
CREATE TABLE tb_blog (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    shop_id     BIGINT       NOT NULL COMMENT '商户 id',
    user_id     BIGINT       NOT NULL COMMENT '作者 id',
    title       VARCHAR(128) DEFAULT '' COMMENT '标题',
    content     TEXT COMMENT '正文',
    images      VARCHAR(2048) DEFAULT '' COMMENT '图片，逗号分隔',
    liked       INT          DEFAULT 0  COMMENT '点赞数（冗余）',
    comments    INT          DEFAULT 0  COMMENT '评论数（冗余）',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_shop_id (shop_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '探店笔记表';

-- ---------------------------------------------------------------------------
-- 6. 优惠券（普通券 / 秒杀券）
--    type: 0 普通券  1 秒杀券
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_voucher;
CREATE TABLE tb_voucher (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    shop_id       BIGINT       DEFAULT NULL COMMENT '所属商户（NULL 表示平台券）',
    title         VARCHAR(64)  NOT NULL COMMENT '标题',
    sub_title     VARCHAR(128) DEFAULT '' COMMENT '副标题',
    rules         VARCHAR(1024) DEFAULT '' COMMENT '使用规则',
    pay_value     BIGINT       NOT NULL DEFAULT 0 COMMENT '支付金额（分）',
    actual_value  BIGINT       NOT NULL DEFAULT 0 COMMENT '抵扣金额（分）',
    type          TINYINT      NOT NULL DEFAULT 0 COMMENT '0 普通券 1 秒杀券',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架 2 下架 3 过期',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_shop_id (shop_id),
    KEY idx_type_status (type, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '优惠券表';

-- ---------------------------------------------------------------------------
-- 7. 秒杀券库存（只有秒杀券才有记录：拆表后行锁只锁这张窄表）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_seckill_voucher;
CREATE TABLE tb_seckill_voucher (
    voucher_id  BIGINT   NOT NULL COMMENT '优惠券 id（与 tb_voucher.id 一致）',
    stock       INT      NOT NULL DEFAULT 0 COMMENT '库存',
    begin_time  DATETIME NOT NULL COMMENT '生效时间',
    end_time    DATETIME NOT NULL COMMENT '失效时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (voucher_id),
    KEY idx_begin_end (begin_time, end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '秒杀券库存表';

-- ---------------------------------------------------------------------------
-- 8. 优惠券订单
--    id 由应用生成（时间戳 << 32 | Redis 自增），不用自增
--    uk_user_voucher 是一人一单的最后一道物理约束
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_voucher_order;
CREATE TABLE tb_voucher_order (
    id          BIGINT   NOT NULL COMMENT '订单 id（应用生成）',
    user_id     BIGINT   NOT NULL COMMENT '下单用户',
    voucher_id  BIGINT   NOT NULL COMMENT '优惠券 id',
    pay_value   BIGINT   NOT NULL DEFAULT 0 COMMENT '实付金额（分）',
    actual_value BIGINT  NOT NULL DEFAULT 0 COMMENT '抵扣金额（分）',
    status      TINYINT  NOT NULL DEFAULT 1 COMMENT '1 待支付 2 已支付 3 已核销 4 已取消 5 退款中 6 已退款',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    pay_time    DATETIME DEFAULT NULL COMMENT '支付时间',
    use_time    DATETIME DEFAULT NULL COMMENT '核销时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_voucher (user_id, voucher_id),
    KEY idx_voucher_id (voucher_id),
    KEY idx_status_create (status, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '优惠券订单表';

-- ---------------------------------------------------------------------------
-- 9. 关注关系
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS tb_follow;
CREATE TABLE tb_follow (
    id             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT   NOT NULL COMMENT '关注者',
    follow_user_id BIGINT   NOT NULL COMMENT '被关注者',
    create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_follow (user_id, follow_user_id),
    KEY idx_follow_user (follow_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '关注表';

-- ============================================================================
-- 初始数据
-- ============================================================================

-- 分类
INSERT INTO tb_shop_type (id, name, icon, sort) VALUES
(1, '美食',   '/icons/food.png',   1),
(2, '娱乐',   '/icons/play.png',   2),
(3, 'KTV',    '/icons/ktv.png',    3),
(4, '丽人',   '/icons/beauty.png', 4),
(5, '健身',   '/icons/fitness.png',5);

-- 用户（登录用验证码，password 留空）
INSERT INTO tb_user (id, phone, nick_name, icon) VALUES
(1, '13800000001', '高浚博',   '/imgs/avatar1.png'),
(2, '13800000002', '测试用户', '/imgs/avatar2.png');

INSERT INTO tb_user_info (user_id, city, introduce, fans, followee) VALUES
(1, '哈尔滨', '哈工大信管在读，做 Java 后端', 4, 1),
(2, '哈尔滨', '一个测试账号', 6, 1);

-- 商户（经纬度取哈尔滨市区，M7 用）
INSERT INTO tb_shop (id, name, type_id, area, address, x, y, avg_price, sold, comments, score, open_hours) VALUES
(1, '老王烧烤（中央大街店）', 1, '中央大街', '道里区中央大街 123 号', 126.616800, 45.773000, 8800,  320, 128, 47, '10:00-22:00'),
(2, '川渝火锅（哈工大店）',   1, '哈工大',   '南岗区西大直街 92 号',   126.632000, 45.741000, 9900,  518, 203, 48, '11:00-23:00'),
(3, '老张砂锅居',             1, '哈工大',   '南岗区西大直街 150 号',  126.633500, 45.740200, 3500,  760,  89, 45, '09:00-21:00'),
(4, 'Manner 咖啡',            1, '中央大街', '道里区友谊路 8 号',      126.615000, 45.776000, 3000,  980,  45, 46, '08:00-20:00'),
(5, '星光影城',               2, '中央大街', '道里区石头道街 66 号',   126.618000, 45.771500, 4500,  210,  76, 44, '10:00-24:00'),
(6, '纯 K（索菲亚店）',       3, '索菲亚',   '道里区透笼街 12 号',     126.621000, 45.772500, 12000, 156,  52, 43, '18:00-02:00'),
(7, '丝域养发',               4, '哈工大',   '南岗区学府路 20 号',     126.636000, 45.738000, 6800,   88,  31, 42, '10:00-21:00'),
(8, '超级猩猩健身',           5, '中央大街', '道里区友谊路 55 号',     126.617200, 45.774800, 15800,  120,  40, 46, '07:00-23:00'),
(9, '老哈尔滨铁锅炖',         1, '索菲亚',   '道里区兆麟街 30 号',     126.622300, 45.770800, 7200,  430, 150, 47, '11:00-22:00'),
(10,'哈工大食堂三楼',         1, '哈工大',   '南岗区西大直街 92 号院内',126.632400, 45.741800, 1500, 1200,  60, 40, '06:30-20:00');

-- 探店笔记
INSERT INTO tb_blog (id, shop_id, user_id, title, content, liked, comments) VALUES
(1, 1, 2, '中央大街这家烧烤是真的顶', '羊肉串很嫩，烤茄子必点，人均 88 左右，晚上要排队。', 12, 3),
(2, 2, 2, '哈工大门口最推荐的火锅', '毛肚脆，锅底香，学生证打九折。', 25, 6),
(3, 4, 1, '自习续命咖啡', '美式 15 一杯，位置就在友谊路，早上八点开。', 8, 1);

-- 关注：1 关注了 2
INSERT INTO tb_follow (user_id, follow_user_id) VALUES (1, 2);

-- 优惠券：一张普通券 + 一张秒杀券
INSERT INTO tb_voucher (id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status) VALUES
(1, 1, '老王烧烤 100 元代金券', '满 200 可用', '仅限堂食，不与其他优惠同享', 5000, 10000, 0, 1),
(2, 2, '川渝火锅 50 元代金券',  '秒杀专享',   '每人限购 1 张，有效期 30 天',  1000, 5000, 1, 1);

-- 秒杀券库存：100 张，从现在开始 30 天有效
INSERT INTO tb_seckill_voucher (voucher_id, stock, begin_time, end_time) VALUES
(2, 100, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY));

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 验证（执行后应看到下面这些数字）
--   tb_shop_type         5
--   tb_user              2
--   tb_shop             10
--   tb_blog              3
--   tb_voucher           2
--   tb_seckill_voucher   1（stock = 100）
--   tb_follow            1
-- ============================================================================
