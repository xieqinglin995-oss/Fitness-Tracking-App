# 安装包

当前版本为 `fitness-1.3.0.apk`，App 名称为“好好健身”，个人自用签名 Release APK，包名 `com.xieqinglin.fitness`，版本 `1.3.0` / `versionCode 7`，最低 Android 8.0。

下载 [fitness-1.3.0.apk](fitness-1.3.0.apk) 后通过安卓系统安装。保持原包名和签名，可直接覆盖原正式版本，保留记录。安装前建议导出备份，不要先卸载旧版。文件 SHA-256 与签名证书指纹记录在 `SHA256SUMS.txt`。

本版补齐力量＋有氧记录：稳定组与加减组、快捷复用、运动计时与分秒、部分完成总结、模板实际值预览、周期排期与训练回看；增加目录自动备份、隐私默认关闭的体重组件、提醒星期和暂缓。详见 [V1.3 实现与验收](../docs/V1.3实现与验收.md)。

新导出备份为 schemaVersion 4，仍可导入 v1/v2/v3；v4 文件不能直接交给旧版恢复。Room 使用显式 1→2 迁移。旧版 [fitness-1.0.0.apk](fitness-1.0.0.apk)、[fitness-1.1.0.apk](fitness-1.1.0.apk)、[fitness-1.1.1.apk](fitness-1.1.1.apk) 与 [fitness-1.2.0.apk](fitness-1.2.0.apk) 保留归档。

发布签名的私钥、密码与本机路径均不随安装包或源码提交。自行用其他密钥重新构建的 APK 无法覆盖安装此正式包；需要先导出 JSON，再卸载旧包、安装新签名版本并恢复。

调试包使用独立包名 `com.xieqinglin.fitness.debug`，与正式版本的数据互相独立。
