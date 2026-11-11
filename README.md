# LocalLife

[![CI](https://github.com/DuduPenguin/locallife/actions/workflows/ci.yml/badge.svg)](https://github.com/DuduPenguin/locallife/actions/workflows/ci.yml)

本地生活平台后端：**登录鉴权 / 缓存治理 / 秒杀链路 / 订单防重**。

> 这是一个"为了讲清楚"而写的项目

## 技术栈

| 组件 | 版本 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.2.5 |
| MyBatis-Plus | 3.5.5 |
| MySQL | 8.0 |
| Redis | 7.x（+ Redisson 3.27.2） |
| RabbitMQ | 3.12 |

## 快速开始

### 1. 起中间件

```bash
docker compose up -d          # MySQL 3306 / Redis 6379 / RabbitMQ 5672、15672
docker compose ps             # 三个都 healthy 再继续
```

管理台：<http://localhost:15672>（guest / guest）

### 2. 灌数据

```bash
# M1 完成后执行
mysql -h127.0.0.1 -uroot -proot locallife < sql/init.sql
```

> 用 docker compose 首次启动时，`sql/` 下的脚本会自动执行；已启动过的话需要手动执行上面这条。

### 3. 启动应用

```bash
mvn spring-boot:run
# 或 IDEA 里跑 LocallifeApplication
```

### 4. 验收

```bash
curl localhost:8080/ping
# {"code":200,"msg":"ok","data":"pong"}

curl localhost:8080/health
# {"code":200,"msg":"ok","data":{"mysql":"ok","redis":"ok"}}
```

两个都是 `ok` → **M0 完成**。

## 模块进度

| 模块 | 内容 | 状态 |
|---|---|---|
| **M0** | 工程骨架、统一返回、异常、健康检查 | ✅ 完成 |
| **M1** | 9 张表 + 初始数据 + Mapper | ✅ 完成 |
| **M2** | 发码 / 登录 / 双层拦截器 / ThreadLocal | ✅ 完成 |
| **M3** | 缓存组件（穿透 / 击穿 / 雪崩）、商户查询 | ✅ 完成 |
| **M4** | 秒杀：Lua 预扣 + MQ 异步 + 消费者幂等 | ✅ 完成 |
| **M5** | 订单状态机、超时取消、对账 | ✅ 完成 |
| **M6** | 社交：点赞 / 关注 / Feed / 签到 | ✅ 完成 |
| **M7** | 地理：GEO 附近商户与距离排序 | ✅ 完成 |
| **M8** | 压测脚本、README、架构文档、LICENSE | ✅ 完成 |

## 压测结果（M4-A vs M4-B）

**测试条件**：本机 4 核，被测服务与压测器同机；库存 100，200 个不同用户并发抢购。

| 方案 | 下单成功 | DB 库存 | 超卖 | 总耗时 | QPS | P50 | P95 | P99 |
|---|---|---|---|---|---|---|---|---|
| 同步版（查库存→扣减→落库） | 109 | **-9** | **9 件** | 1328 ms | 151 | 1228 ms | 1271 ms | — |
| **Lua + MQ 版** | **100** | **0** | **0 件** | **238 ms** | **840** | **156 ms** | **227 ms** | **233 ms** |

**复现方式**：

```bash
# 1. 重置数据
mysql ... -e "UPDATE tb_seckill_voucher SET stock=100 WHERE voucher_id=2; DELETE FROM tb_voucher_order WHERE voucher_id=2;"
# 2. 重启应用（重新预热库存）
# 3. 准备 300 个虚拟用户登录态
powershell -ExecutionPolicy Bypass -File script/prepare_tokens.ps1
# 4. 压测
java script/SeckillBench.java 200 sync   # 会超卖
java script/SeckillBench.java 200 lua    # 零超卖
# 5. 等 2 秒（落库是异步的）再查库
```

> **关于指标可信度**：被测服务与压测器同机，MQ 消费者会与压测线程抢 CPU，
> 所以 P95 只反映量级关系，不能当生产容量。生产环境应分开部署并加监控。

## 数据说明

初始的 10 家商户是手工编的，够跑通流程，但撑不起两件需要数据量的事：
GEO 距离排序（一个商圈只有 1 家店）和 RAG 检索评测（知识库太小，命中率天然接近 100%）。

所以补了一批**真实存在的商户**：

| 项 | 说明 |
|---|---|
| 来源 | 哈尔滨市商务局 2024-02 发布的[美食地图](https://www.hrbtv.net/folder137/2024-02-15/877233.html)（4 个主城区、50+ 家餐饮门店） |
| 真实字段 | 店名、门牌地址、所属城区/商圈 |
| 估算字段 | 营业时间、人均、评分、经纬度（演示用，不代表真实经营数据） |
| 规模 | **80 家商户**（10 家初始 + 70 家新增）/ 34 张券（3 张秒杀）/ 124 条笔记 |
| 生成器 | [tools/gen_seed.py](tools/gen_seed.py)，固定随机种子，结果可复现 |

```bash
python tools/gen_seed.py
mysql ... locallife --default-character-set=utf8mb4 < sql/seed_shops.sql
```

> 为什么不用爬虫抓点评/美团：有反爬和合规风险。
> 用公开的政府发布名单 + 自己估算经营数据，既真实又不越界 —— 面试被问「数据哪来的」能直接答。

## 目录结构

```
locallife
├── pom.xml
├── docker-compose.yml          可选：一键起 MySQL / Redis / RabbitMQ
├── sql/
│   ├── init.sql                9 张表 + 初始数据
│   ├── seed_shops.sql          80 家商户（生成，可重复执行）
│   ├── reset.sql               重置秒杀库存与订单
│   └── verify.sql              建表结果校验
├── tools/
│   └── gen_seed.py             种子数据生成器（固定随机种子，结果可复现）
├── script/
│   ├── prepare_tokens.ps1      压测前准备 300 个虚拟用户登录态
│   ├── SeckillBench.java       压测器（JDK 单文件运行，无依赖）
│   └── README.md
├── http/
│   ├── user.http               登录相关请求
│   ├── shop.http               缓存与附近商户
│   ├── seckill.http            秒杀两版对比
│   ├── order.http              支付 / 取消 / 对账
│   └── social.http             点赞 / 关注 / Feed / 签到
├── docs/
│   └── architecture.md         架构图、表设计、6 个技术决策、订单生命周期、压测方法
└── src/main/java/com/gao/locallife
    ├── LocallifeApplication.java
    ├── common/        统一返回、业务异常、ThreadLocal、Redis key 常量、秒杀返回码
    ├── config/        分页插件、拦截器注册、RabbitMQ 拓扑、订单延迟队列
    ├── controller/    用户 / 商户 / 秒杀订单 / 笔记 / 关注 / 签到 / 健康检查
    ├── dto/           登录表单、用户、Feed 分页结果
    ├── entity/        9 张表对应实体
    ├── interceptor/   双层登录拦截器（识别用户 + 强制登录）
    ├── cache/         缓存组件 CacheClient（穿透 / 互斥锁 / 逻辑过期）
    ├── mq/            秒杀消息生产与消费、订单超时取消（手动 ACK + 死信）
    ├── mapper/        数据访问（BaseMapper + 注解 SQL）
    ├── service/impl/  业务实现
    ├── id/            订单号生成器（时间戳 + 日序列）
    └── task/          缓存预热、GEO 预热、库存预热、定时对账
```

## 同一库里的另一张表

`kb_chunk` 是 **locallife-agent** 项目的表（知识库分块 + ngram 全文索引，给关键词召回用），
建在同一个 `locallife` 库、方便两个项目共用一份数据。
它不属于本项目，`init.sql` 里没有它，建表语句在 agent 仓库的 `sql/agent_schema.sql`。

## 约定

- Redis key 全部集中在 `common/RedisConstants`，不散落在业务代码里
- 表名统一 `tb_` 前缀，靠 `mybatis-plus.global-config.db-config.table-prefix` 自动拼，实体类不写 `@TableName`
- 接口统一返回 `Result<T>`，异常统一走 `GlobalExceptionHandler`

## 参考与说明

表结构与模块划分沿用了公开教程的经典设计（本地生活类项目的通用模型），
业务代码、压测脚本、调优过程与文档均为自行实现。在此基础上做了这些针对性改造：

- 秒杀同步链路改为 **Lua 原子预扣 + RabbitMQ 异步落库**，并用两版对照压测（超卖 9 件 → 0 件）
- 补上**订单状态机、超时取消与守恒式对账**（原设计没有这部分）
- 缓存组件抽成泛型 + 函数式接口，按热度分级选互斥锁或逻辑过期
- 消费端三层幂等（订单号 / 用户券 / 唯一索引）+ 手动 ACK + 死信

各模块的技术选型与取舍见 [docs/architecture.md](docs/architecture.md)。

