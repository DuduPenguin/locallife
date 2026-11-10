# LocalLife

本地生活平台后端：**登录鉴权 / 缓存治理 / 秒杀链路 / 订单防重**。

> 这是一个"为了讲清楚"而写的项目：每个模块都追求
> ## 快速开始

**方式一（推荐给已有环境的同学）**：本机已装 MySQL 8 / Redis 7，改 `application.yml` 的地址密码即可

**方式二（一键起环境）**：`docker compose up -d`，全部端口已配好。

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
| **M1** | 9 张表 + 初始数据 + Mapper | ⬜ 待做 |
| **M2** | 发码 / 登录 / 双层拦截器 / ThreadLocal | ⬜ 待做 |
| **M3** | 缓存组件（穿透 / 击穿 / 雪崩）、商户查询 | ⬜ 待做 |
| **M4** | 秒杀：Lua 预扣 + MQ 异步 + 消费者幂等 | ⬜ 待做 |
| **M5** | 订单 ID、条件更新、唯一索引、对账 | ⬜ 待做 |
| **M6** | 社交：点赞 / 关注 / Feed / 签到（选做） | ⬜ 待做 |
| **M7** | 地理：GEO 附近商户（选做） | ⬜ 待做 |
| **M8** | 压测脚本、README 完善、docker-compose | ⬜ 待做 |

## 目录结构

```
src/main/java/com/gao/locallife
├── LocallifeApplication.java
├── common/          统一返回、异常、ThreadLocal、Key 常量
├── config/          MyBatis-Plus 等配置
├── controller/      HTTP 入口
├── dto / vo / entity
├── mapper           数据访问
├── service/impl     业务
├── interceptor      登录拦截器（M2）
├── cache            缓存组件（M3）
├── mq               消息生产/消费（M4）
├── id               订单号生成（M5）
└── task             定时对账（M5）
```

## 约定

- Redis key 全部集中在 `common/RedisConstants`，不散落在业务代码里
- 表名统一 `tb_` 前缀，靠 `mybatis-plus.global-config.db-config.table-prefix` 自动拼，实体类不写 `@TableName`
- 接口统一返回 `Result<T>`，异常统一走 `GlobalExceptionHandler`

## 参考与说明

本项目参考了公开教程的**架构思路**（表结构、模块划分），业务代码为自行实现。
未实现部分会在对应模块的 README 段落中标注。
