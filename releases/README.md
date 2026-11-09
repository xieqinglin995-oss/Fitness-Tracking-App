# 安装包

`fitness-1.0.0.apk` 为个人自用签名 Release APK，包名 `com.xieqinglin.fitness`，版本 `1.0.0` / `versionCode 1`，最低 Android 8.0。

下载 [fitness-1.0.0.apk](fitness-1.0.0.apk) 后通过安卓系统安装。文件的 SHA-256 与签名证书指纹记录在 `SHA256SUMS.txt`。后续更新须沿用原签名并递增版本，安装前建议导出备份。

发布签名的私钥、密码与本机路径均不随安装包或源码提交。自行用其他密钥重新构建的 APK 无法覆盖安装此正式包；需要先导出 JSON，再卸载旧包、安装新签名版本并恢复。
