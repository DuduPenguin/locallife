# script 目录：压测与工具

| 文件 | 作用 |
|---|---|
| `prepare_tokens.ps1` | 给 300 个虚拟用户写登录态到 Redis（免去 300 次短信登录） |
| `SeckillBench.java` | 秒杀压测，输出 P50/P95/max、成功/失败数 |

## 压测三步

### 1. 重置库存和数据

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --host=127.0.0.1 --user=root --password=123456 --database=locallife -e "UPDATE tb_seckill_voucher SET stock=100 WHERE voucher_id=2; DELETE FROM tb_voucher_order WHERE voucher_id=2;"
```

### 2. 准备虚拟用户登录态

```powershell
powershell -ExecutionPolicy Bypass -File script\prepare_tokens.ps1
```

> 原理：拦截器只读 `login:token:{token}` 这个 Hash，所以直接写 Redis 就能伪造登录态。
> 这不算作弊——压测关注的是并发行为，不是登录流程。

### 3. 压测

```powershell
java script\SeckillBench.java 200 sync
```

## 结果怎么读

压测输出成功后，马上查数据库：

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --host=127.0.0.1 --user=root --password=123456 --database=locallife --table -e "SELECT stock FROM tb_seckill_voucher WHERE voucher_id=2; SELECT COUNT(*) AS orders FROM tb_voucher_order WHERE voucher_id=2;"
```

| 现象 | 含义 |
|---|---|
| `stock < 0` | **超卖**（同步版的 bug 被压出来了） |
| `orders > 100` | 卖出去的比库存多 |
| `stock >= 0 且 orders = 100` | 正确 |

## 对比两版

```powershell
# 同步版（会有超卖）
java script\SeckillBench.java 200 sync

# Lua + MQ 版（应该 stock=0, orders=100）
java script\SeckillBench.java 200 lua
```
