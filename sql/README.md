# sql 目录说明

`docker compose up` 首次启动时，MySQL 会**按文件名顺序**执行本目录下的 `*.sql`。

| 文件 | 内容 | 属于 |
|---|---|---|
| `init.sql` | 建库建表 + 初始数据（M1 提供） | M1 |

注意：
- 只在**数据卷为空**时自动执行。已启动过就需要手动 `mysql < sql/init.sql`，或 `docker compose down -v` 清卷重来。
- 脚本要写成**幂等**的（`CREATE TABLE IF NOT EXISTS`、`INSERT ... ON DUPLICATE KEY UPDATE`），方便反复执行。
