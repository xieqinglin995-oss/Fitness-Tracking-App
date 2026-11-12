# 安装包

当前版本为 `fitness-1.1.1.apk`，App 名称为“好好健身”，个人自用签名 Release APK，包名 `com.xieqinglin.fitness`，版本 `1.1.1` / `versionCode 5`，最低 Android 8.0。

下载 [fitness-1.1.1.apk](fitness-1.1.1.apk) 后通过安卓系统安装。保持原包名和签名，可直接覆盖原正式版本，保留记录。安装前建议导出备份，不要先卸载旧版。文件 SHA-256 与签名证书指纹记录在 `SHA256SUMS.txt`。

本版包含批量完成与撤销、称重框直接切换斤与公斤、体重与负重独立单位、上次训练参考，以及今天、计划、趋势和设置页面重新设计。详见 [优化与调研](../docs/V1.1优化与调研.md) 和 [V1.1 验收记录](../docs/V1.1验收记录.md)。

新导出备份为 schemaVersion 2，仍可导入 v1 备份；v2 文件不能直接交给旧版恢复。旧版 [fitness-1.0.0.apk](fitness-1.0.0.apk) 与 [fitness-1.1.0.apk](fitness-1.1.0.apk) 保留用于归档。

发布签名的私钥、密码与本机路径均不随安装包或源码提交。自行用其他密钥重新构建的 APK 无法覆盖安装此正式包；需要先导出 JSON，再卸载旧包、安装新签名版本并恢复。

调试包使用独立包名 `com.xieqinglin.fitness.debug`，与正式版本的数据互相独立。
