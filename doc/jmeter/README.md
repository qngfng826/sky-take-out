# JMeter 压测产物说明

| 文件/目录 | 说明 |
|---|---|
| `sky-cache-test.jmx` | 万能压测脚本：catId / threads / dur / ramp / port / token 全部参数化（`-J` 传入），三组场景共用 |
| `hot.jtl` / `report-hot` | 场景① 缓存命中（改造后，50 并发 × 30s） |
| `miss2.jtl` / `report-miss2` | 场景② 缓存击穿（50 线程瞬时齐发 ramp=0，改造后） |
| `pen.jtl` / `report-pen` | 场景③ 缓存穿透（categoryId=-999，60s，改造后） |
| `base-*.jtl` / `report-base-*` | 基线（改造前课程代码容器化）对应场景 |

- 指标汇总与取证结论见 [doc/改造说明.md](../改造说明.md) §1.5；HTML 报告打开各 `report-*/index.html`。
- `.jtl` 与 `report-*/` 已被 .gitignore 排除，仓库只保留脚本与本说明（数据快照在改造说明的表格里）。
- 复现方法（含 token 生成与 general log 取证）见 [doc/改造说明.md](../改造说明.md) §1.5。
