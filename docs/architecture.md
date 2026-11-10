# 架构与技术决策

## 1. 整体结构

```
                    ┌──────────────────────────┐
   客户端 ──token──► │ RefreshTokenInterceptor  │ order=0，/** 全拦
                    │  识别用户 + 阈值续期      │
                    └────────────┬─────────────┘
                                 ▼
                    ┌──────────────────────────┐
                    │   LoginInterceptor       │ order=1，排除白名单
                    │  ThreadLocal 无用户 → 401 │
                    └────────────┬─────────────┘
                                 ▼
                    ┌──────────────────────────┐
                    │      Controller          │
                    └────────────┬─────────────┘
                                 ▼
                    ┌──────────────────────────┐
                    │       Service            │
                    └───┬──────────┬───────────┘
                        │          │
             ┌──────────▼──┐   ┌───▼──────────┐   ┌─────────────┐
             │    Redis    │   │  RabbitMQ    │   │    MySQL    │
             │ 缓存/库存    │   │ 秒杀订单异步  │   │  权威数据    │
             │ 登录态/GEO   │   │              │   │             │
             └─────────────┘   └───┬──────────┘   └─────▲───────┘
                                   │                    │
                              ┌────▼────────────────────┘
                              │  SeckillOrderConsumer
                              │  幂等 + 条件更新 + 手动 ACK
                              └──────────────────────────
```

## 2. 数据表（9 张）

| 表 | 说明 | 关键索引 |
|---|---|---|
| `tb_user` | 账号 | `uk_phone` |
| `tb_user_info` | 用户详情（与主表冷热分离） | 主键 user_id |
| `tb_shop` | 商户（含经纬度） | `idx_type_id`、`idx_name`、`idx_score` |
| `tb_shop_type` | 分类 | — |
| `tb_blog` | 探店笔记 | `idx_shop_id`、`idx_user_id` |
| `tb_voucher` | 优惠券 | `idx_shop_id` |
| `tb_seckill_voucher` | 秒杀库存（单独拆表） | 主键 voucher_id |
| `tb_voucher_order` | 订单 | **`uk_user_voucher`**、`idx_status_create` |
| `tb_follow` | 关注关系 | `uk_user_follow` |

## 3. Redis Key 规范

统一 `业务:对象:标识` 分层，全部集中在 `common/RedisConstants`。

| 用途 | Key | 类型 | TTL |
|---|---|---|---|
| 验证码 | `login:code:{phone}` | String | 120s |
| 登录态 | `login:token:{token}` | Hash | 1800s（阈值续期） |
| 商户缓存 | `cache:shop:{id}` | String(JSON) | 1800s + 随机 |
| 缓存空值 | `cache:shop:{id}` = `""` | String | 120s |
| 逻辑过期 | `cache:shop:logical:{id}` | String(JSON) | **无 TTL** |
| 重建锁 | `lock:shop:{id}` | String | 10s |
| 秒杀库存 | `seckill:stock:{id}` | String | 无 |
| 已购集合 | `seckill:order:{id}` | Set | 无 |
| 活动时间 | `seckill:time:{id}` | String | 无 |
| 订单序列 | `icr:order:{yyyyMMdd}` | String | 无 |

> 秒杀相关 key 带 `{}` hash tag，保证集群模式下同槽，避免 `CROSSSLOT`。

## 4. 六个关键技术决策

### 4.1 登录用 Redis Hash + 随机 Token，不用 JWT

| 需求 | JWT | Redis Token |
|---|---|---|
| 主动失效（登出/封号） | ❌ 只能等过期 | ✅ `DEL` 即可 |
| 滑动续期 | ❌ 过期时间写死在签名里 | ✅ `EXPIRE` |
| 单次校验开销 | 0 次 IO | 1 次 Redis |

业务需要"30 分钟不活跃才过期"和"能踢人"，JWT 两样都做不到。

### 4.2 缓存用"先更库再删缓存"，不用更新缓存

更新缓存有两个毛病：**无效写**（key 可能没人读）+ **并发覆盖**（旧值盖新值）。
删缓存让下次读自然回填。剩余窗口用延迟双删压缩，但明确这是**最终一致**。

### 4.3 热点 Key 按热度分级：互斥锁 / 逻辑过期

| | 互斥锁 | 逻辑过期 |
|---|---|---|
| 一致性 | 强 | 弱（短时旧值） |
| 可用性 | 差（阻塞） | 好（不阻塞） |
| 适用 | 价格、库存 | 商户信息、文章详情 |

**不是二选一，是按场景选。**

### 4.4 秒杀用 Lua，不用分布式锁

锁把并发串行化，QPS 掉到几百；Lua 靠 Redis 单线程执行，无锁开销，一次往返完成"判断 + 扣减"。

> Lua 只保证**执行不被打断**，**不回滚** —— 所以所有校验都放在写入之前。

### 4.5 落库异步化，用 MQ 削峰

同步链路从 4 次 DB 操作减为 2 次 Redis 往返，数据库从"瞬时 200 并发"变成"匀速消费"。

### 4.6 三层幂等，不只靠"先查再写"

| 层 | 手段 | 强度 |
|---|---|---|
| 1 | 按 orderId 查重 | 中（挡消息重投） |
| 2 | 按 user + voucher 查重 | 中 |
| 3 | **`uk_user_voucher` 唯一索引** | **高（并发安全）** |

"先查再写"在并发下一定有窗口，**最终要靠数据库约束兜底**。

## 5. 订单生命周期

`
下单(Lua预扣) --> 落库 --> 发延迟消息(30min) --> 到期未支付 --> 取消 + 回补
                              |
                              +--> 期间用户支付 --> 状态机 1->2 --> 延迟消息到期时幂等跳过
`

### 状态机

| 状态 | 值 | 允许的流转 |
|---|---|---|
| 待支付 | 1 | → 2 已支付 / → 4 已取消 |
| 已支付 | 2 | → 3 已核销 |
| 已取消 | 4 | 终态 |

所有流转都用 UPDATE ... WHERE id = ? AND status = 1 这种**带状态条件的更新**，
天然幂等：重复调用返回 0 行，不需要额外加锁。

### 取消订单要做三步

1. 状态机置为已取消（cancelIfPending）
2. DB 库存 +1（increaseStock）
3. Redis 库存 +1 并移出已购集合（Lua，先判 SISMEMBER 防重复回补）

**第 3 步最容易漏** —— 漏了就会少卖：DB 有库存但 Redis 没有，用户抢不到。

### 延迟消息为什么用 TTL + 死信而不是插件

生产环境装延迟插件需要运维配合。TTL + DLX 是零依赖方案：
order.delay.queue 设整队 TTL，消息过期后自动变成死信转投 order.cancel.queue。

**注意**：整队 TTL 是统一值，先进先出成立；如果每条消息 TTL 不同，就会出现队头阻塞
（前面不过期，后面的也出不去）。

### 对账的两个守恒式

`
DB:    dbStock    + dbSold    == 初始库存
Redis: redisStock + redisSold == 初始库存
一致性: dbSold == redisSold
`

对账只负责**发现**，不负责自动修复 —— 自动改容易把问题放大。

## 6. 压测方法

```bash
# 1. 重置（库 + Redis 由重启应用重新预热）
mysql ... -e "UPDATE tb_seckill_voucher SET stock=100 WHERE voucher_id=2; DELETE FROM tb_voucher_order WHERE voucher_id=2;"

# 2. 准备 300 个虚拟用户的登录态（直接写 Redis，免去 300 次短信登录）
powershell -ExecutionPolicy Bypass -File script/prepare_tokens.ps1

# 3. 压测
java script/SeckillBench.java 200 sync   # 同步版
java script/SeckillBench.java 200 lua    # Lua + MQ 版

# 4. 等 2 秒（落库是异步的）再查库
```

**压测器注意点**：不要把 HttpClient 的线程池也开成 200 —— 本机线程数翻倍会让 P95 严重失真。
另外要加预热阶段，避免 JIT 冷启动污染首轮数据。

## 7. 未实现 / 后续规划

| 项 | 状态 | 说明 |
|---|---|---|
| 库存分桶 | 未实现 | 十万级 QPS 下单个库存 key 会成热点 |
| 限流熔断 | 未实现 | 计划 Sentinel + 线程池隔离 |
| 对账自动修复 | 未实现 | 目前只检测和告警，修复靠人工/重放死信 |
| 监控告警 | 未实现 | 计划 Micrometer + 慢查询/堆积告警 |

## 8. 参考说明

表结构与模块划分沿用公开教程的经典设计；业务代码、压测脚本、调优过程与本文档为自行实现。
与原设计的差异、以及仍未实现的部分，分别在本文档第 4 节与第 7 节说明。
