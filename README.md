# LocalLife

本地生活平台后端：**登录鉴权 / 缓存治理 / 秒杀链路 / 订单防重**。

> 这是一个"为了讲清楚"而写的项目：每个模块都追求**能跑通 + 能压测 + 能解释取舍**，而不是堆功能。

## 核心数据（200 并发抢 100 库存）

| 方案 | 下单成功 | DB 库存 | 超卖 | P95 | QPS |
|---|---|---|---|---|---|
| 同步落库（查库存→扣减→落库） | 109 | **-9** | **9 件** | 1271 ms | 151 |
| **Lua 预扣 + MQ 异步** | **100** | **0** | **0 件** | **227 ms** | **840** |

数据可复现，方法见 [`docs/architecture.md`](docs/architecture.md) 第 5 节和 [`script/`](script/)。

## 这个项目解决什么问题

| 问题 | 手段 |
|---|---|
| 热点数据打穿数据库 | 泛型缓存组件：空值防穿透 / 互斥锁 / 逻辑过期 / 随机 TTL 防雪崩 |
| 秒杀超卖 | Lua 原子预扣 + DB 条件更新 + 唯一索引，三层兜底 |
| 瞬时洪峰 | RabbitMQ 异步落库 + 手动 ACK + 死信 |
| 多人多实例登录 | Redis Hash 登录态 + 双层拦截器 + 阈值滑动续期 |

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
| **M5** | 订单 ID、条件更新、唯一索引、对账 | ⬜ 待做 |
| **M6** | 社交：点赞 / 关注 / Feed / 签到（选做） | ⬜ 待做 |
| **M7** | 地理：GEO 附近商户（选做） | ⬜ 待做 |
| **M8** | 压测脚本、README 完善、docker-compose | 🚧 进行中 |

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

## 目录结构

```
├── pom.xml
├── docker-compose.yml          可选：一键起 MySQL / Redis / RabbitMQ
├── sql/
│   ├── init.sql                9 张表 + 初始数据
│   └── verify.sql              建表结果校验
├── script/
│   ├── prepare_tokens.ps1      压测前准备 300 个虚拟用户登录态
│   ├── SeckillBench.java       压测器（JDK 单文件运行，无需依赖）
│   └── README.md
├── http/
│   ├── user.http               登录相关请求（IDEA HTTP Client 可直接点）
│   ├── shop.http               缓存验证
│   └── seckill.http            秒杀两版对比
├── docs/
│   └── architecture.md         架构图、表设计、6 个技术决策、压测方法、未实现清单
└── src/main/java/com/gao/locallife
    ├── LocallifeApplication.java
    ├── common/        统一返回、业务异常、ThreadLocal、Redis key 常量
    ├── config/        MyBatis-Plus 分页、拦截器注册、RabbitMQ 拓扑
    ├── controller/    HTTP 入口
    ├── dto / entity   传输对象与实体
    ├── mapper         数据访问（BaseMapper + 注解 SQL）
    ├── service/impl   业务实现
    ├── interceptor    双层登录拦截器
    ├── cache          缓存组件 CacheClient（穿透 / 互斥锁 / 逻辑过期）
    ├── mq             秒杀消息生产与消费（手动 ACK + 死信）
    ├── id             订单号生成器
    └── task           缓存预热、库存预热
```

## 接口一览

| 方法 | 路径 | 说明 | 需要登录 |
|---|---|---|---|
| POST | `/user/code?phone=` | 发验证码（开发期直接返回） | ❌ |
| POST | `/user/login` | 登录换 token | ❌ |
| POST | `/user/logout` | 登出 | ✅ |
| GET | `/user/me` | 当前用户 | ✅ |
| GET | `/shop/{id}` | 商户详情（空值防穿透） | ❌ |
| GET | `/shop/logical/{id}` | 商户详情（逻辑过期） | ❌ |
| GET | `/shop-type/list` | 分类列表 | ❌ |
| GET | `/shop/of/type?typeId=&current=` | 分类分页 | ❌ |
| POST | `/voucher-order/seckill/{id}` | 秒杀下单（Lua + MQ） | ✅ |
| POST | `/voucher-order/seckill-sync/{id}` | 秒杀下单（同步版，仅压测对比） | ✅ |
| GET | `/health` | MySQL / Redis 连通性 | ❌ |

## 约定

- Redis key 全部集中在 `common/RedisConstants`，业务代码里不出现硬编码 key
- 秒杀相关 key 带 `{}` hash tag（`seckill:stock:{2}`），保证集群同槽
- 表名统一 `tb_` 前缀，靠 `mybatis-plus...table-prefix` 自动拼，实体类不写 `@TableName`
- 接口统一返回 `Result<T>`，异常集中由 `GlobalExceptionHandler` 处理
- **凭据不写死**：`application.yml` 用 `${MYSQL_PASSWORD:123456}` 这种"环境变量优先 + 本地默认值"的写法，
  本地直接能跑，部署时用环境变量覆盖

## 参考与说明

本项目参考了公开教程的**架构思路**（表结构、模块划分），业务代码、压测脚本与调优过程为自行实现。
未实现的部分在 [`docs/architecture.md`](docs/architecture.md) 第 6 节明确列出，代码里对应位置也留了 TODO。

