# 安装包

当前版本为 `fitness-1.2.0.apk`，App 名称为“好好健身”，个人自用签名 Release APK，包名 `com.xieqinglin.fitness`，版本 `1.2.0` / `versionCode 6`，最低 Android 8.0。

下载 [fitness-1.2.0.apk](fitness-1.2.0.apk) 后通过安卓系统安装。保持原包名和签名，可直接覆盖原正式版本，保留记录。安装前建议导出备份，不要先卸载旧版。文件 SHA-256 与签名证书指纹记录在 `SHA256SUMS.txt`。

本版增加专注训练、逐组确认、底部休息计时、动作搜索与复用、月历、页面位置记忆、主题、可选提醒、CSV 与本机恢复点。详见 [优化与调研](../docs/V1.2体验优化与调研.md) 和 [V1.2 验收记录](../docs/V1.2验收记录.md)。

新导出备份为 schemaVersion 3，仍可导入 v1/v2；v3 文件不能直接交给旧版恢复。旧版 [fitness-1.0.0.apk](fitness-1.0.0.apk)、[fitness-1.1.0.apk](fitness-1.1.0.apk) 与 [fitness-1.1.1.apk](fitness-1.1.1.apk) 保留归档。

发布签名的私钥、密码与本机路径均不随安装包或源码提交。自行用其他密钥重新构建的 APK 无法覆盖安装此正式包；需要先导出 JSON，再卸载旧包、安装新签名版本并恢复。

调试包使用独立包名 `com.xieqinglin.fitness.debug`，与正式版本的数据互相独立。
