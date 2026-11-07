# 雨课堂助手 Android · GitHub 版

版本：3.2（31）

- applicationId / namespace：`com.ykt.assistant`
- Android 8.0+，compileSdk / targetSdk 35，JDK 17，Gradle 8.10.2。
- 默认使用标准 Android 调试签名，release 构建默认不签名；可用 -PassistantApplicationId=starx.ykt.assistant 构建原私有包名。
- GitHub 默认包（com.ykt.assistant）启用有道云静默更新检测，与自动登录 / 课程加载并行，无等待弹窗；强制更新不可跳过；检测失败按旧规则跳转项目主页并退出。
- 二维码轮次隔离、失效撤图、连续等待与重取；资料查询单独重试。
- 按日期保存脱敏诊断日志，可在“我的”中导出，密码、token 和 Cookie 值不写入日志。
- 二维码 / 手机号 / 邮箱密码 / 短信登录、全屏官方验证、服务端位置签到、课堂凭证、动态缓存、课件与下载功能整合保留。
- 位置签到不申请设备定位权限；只提交当前课堂服务端位置，并查询 signed 状态确认。

完整功能、GitHub Actions 和本机构建说明见[项目 README](../README.md)。

密码与短信验证见[账号登录](../docs/account-login.md)，位置签到见[服务端位置签到](../docs/location-checkin.md)。
