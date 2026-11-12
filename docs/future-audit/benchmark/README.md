# 探索实验

这些源码用于复现后续方案中的现状分析，基线为 `0c7e9f4`。它们没有加入 App 构建和持续集成，也不证明任何待实现功能已通过测试。

- `generate-history.py`：生成截至 2026-11-12 的 1/5/10 年人工历史，无用户记录。
- `FutureBench.java`：调用生产编译类，预热后测量校验、JSON 与统计；中位数见技术审查。
- `BoundaryProbe.java`：日期回拨、草稿统计口径、恢复点未来时间与对照目录实验。
- `desktop-jvm.json`、`boundary.txt`：本轮原始输出。
- `android-debug-observation.txt`：独立调试模拟器该进程的探索日志。Skipped frames 只是定位线索，没有成功取得正式帧耗时分布。

Windows 下已具备 JDK 17、Python、SDK 和项目依赖缓存时，可从仓库根目录运行：

```powershell
.\docs\future-audit\benchmark\run-benchmark.ps1 -OutputDirectory D:\tmp\fitness-research-repeat
# Python 不在 PATH 时，另传 -PythonCommand 指向 python.exe
```

脚本离线编译现有 Debug Kotlin 类，读取 Gradle 缓存中的固定版本依赖，将 Java class、生成 JSON 和结果放到指定输出目录，不自动安装 App、不改正式数据库。依赖版本随未来项目升级而变化时需相应调整；不要把脚本当成长期稳定的构建接口。

样本按每周一/三/五生成 6 个项目，其中 3 个力量、2 个时长、1 个距离，每日一条体重。它只表达数据规模，不是用户训练计划。生成的五年 JSON 约 3.24 MB、十年约 6.48 MB，不随本次文档提交，以免把模拟记录混进发布内容。

结果受 JIT、GC、宿主负载及执行顺序影响；不是标准 JMH、Macrobenchmark 或手机验收成绩。第一次解码也不包含在预热后的中位数中。未来修改类签名、校验或备份格式后，先确认实验仍在比较同一种工作。

设备探索仅涉及 `emulator-5556` 上的独立 `.debug` 包和可恢复的字体/显示覆盖；长历史未恢复进正式包。所附截图来自人工数据，不是出厂预置内容。
