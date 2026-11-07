# 3.2 功能重建与交付说明

基于公开原生客户端重新整合 3.2 功能，不再依赖丢失的 29 版源码。已根据功能记录重建：手机号 / 邮箱密码登录、短信验证码登录、腾讯安全验证全屏显示、服务端位置签到及相关取消 / 隔离保护。

同时保留并整合二维码新状态机、脱敏诊断日志、静默更新检测与强制更新规则，以及原有课堂凭证、动态缓存、历史原版课件、PPTX / ZIP 与单页下载、图片缩放 / 左右切换和自定义 User-Agent。

这是功能重建版本，不是对旧 29 二进制逐字节恢复。验证采用模拟接口、原生宿主布局和会话回归，未使用真实账号或真实短信 / 课堂进行操作。

默认 applicationId / namespace 为 com.ykt.assistant，版本 3.2 / 31；私有 APK 可通过 assistantApplicationId 参数选择 starx.ykt.assistant，签名仍在源码外处理。GitHub 工作流和 Secrets / key 配置保持不变。私有签名在源码外处理，私钥、密码不得放入源码或诊断日志；GitHub 默认构建使用 Android 调试签名。

功能回归共 168 项通过，Debug / Release 构建与 Lint 均通过。本次交付为 GitHub 默认包名 com.ykt.assistant 的 3.2 版本，包含有道云更新检测，未改动 GitHub 签名或 Secrets。
