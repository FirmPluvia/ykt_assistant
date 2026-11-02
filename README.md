# 雨课堂助手 · ykt_assistant

Android 8.0+ 原生直连客户端，版本 **3.0（versionCode 26）**。

> **开源范围声明：本仓库仅开源 3.0 版本源码，后续版本不再开源。** 请以本仓库内容为准，不要将其与后续闭源版本混淆。

## 这是什么

面向雨课堂的第三方辅助客户端，手机直接连接雨课堂 HTTP / WebSocket 接口，**无中继服务器、无 WebView、无演示账号**。

支持的雨课堂服务：标准、荷塘、长江、黄河；支持二维码登录、切换服务、刷新确认、退出账号。

## 功能

- 首页优先显示正在上课的课程；优先复用本地课堂凭证，缺少凭证时确认签到并进入课堂。
- 课堂凭证后台静默查询；快速命中直接入课，超过 500ms 才显示可取消提示。返回、切换页面或退出账号会取消待显示提示和迟到入课。
- 动态首屏显示 10 条；加载更多优先展开连续缓存，再按原始游标请求后续记录。
- 动态、课堂 / 签到详情、课件页信息按账号和服务器加密缓存；账号注销时撤销并清理。
- 实时 / 原版历史课件查看、刷新；图片双指缩放、左右滑动和长按单页保存。
- 在手机生成图片版 PPTX 和图片 ZIP，支持保存进度和取消。
- 自定义 User-Agent，设备默认值读取实际 Android 版本、型号与 Build 编号。

此版本**没有应用更新检测或强制更新功能**，不会请求有道笔记，也不会因更新信息获取失败跳转项目页面或退出。项目主页仅可由用户在“我的 → 关于应用”主动打开。课程和动态的刷新功能保留。

## 包名说明

本公开源码的 applicationId / namespace 为 **`com.ykt.assistant`**，与作者自有签名版本包名不同，会作为独立应用安装，需要重新登录，旧包名数据不会自动迁移。

## Release 说明

Release 里**只上传开发者构建的 APK**，即作者自有签名版本的安装包，不附加其他构建产物。

注意：GitHub 会给 Release 的 tag 自动生成源码压缩包下载项，那是 GitHub 的固定行为，不是作者上传的附件；Release 里作者手动上传的附件只有开发者构建的 APK 一个。

## GitHub 构建

将本仓库内容（包括 `.github` 隐藏目录）作为仓库根目录。推送 Android 源码、提交 PR 或在 Actions 中手动运行 **Build Android APK** 后，会执行单元测试、APK 构建与 Lint 检查。

构建完成后，从该次运行的 **Artifacts → ykt_assistant-github-apk** 下载 APK。

使用 Android 标准调试签名，不读取私有签名配置；不包含签名文件、密码或 GitHub Secrets。GitHub 托管 Runner 可能在不同运行中生成不同调试密钥，因此不同构建不保证可直接覆盖安装。需要固定签名发布时，可自行配置持久化签名，密钥应单独保管。

## 本机构建

准备 JDK 17、Android SDK 35、Build Tools 35.0.0、Gradle 8.10.2，然后运行：

```sh
cd android
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

也可使用项目自带 Gradle Wrapper（需 `android/gradle/wrapper/gradle-wrapper.jar` 在位）：

```sh
cd android
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

产物：`android/app/build/outputs/apk/debug/app-debug.apk`。本机 SDK 可通过 `ANDROID_HOME` 或未纳入源码的 `android/local.properties` 配置。

`release` 默认不签名，需自行配置正式签名。已移除所有原签名密钥及密码；应用版本名和版本号保留为 3.0 / 26。

## 数据与隐私

- Cookie 和课堂凭证通过 Android Keystore 加密保存，本地缓存优先位于 `Android/data/com.ykt.assistant/files/`，不记录本地日志。
- 测试使用模拟接口与脱敏数据，不使用真实账号 Cookie；真机登录、Keystore 和课堂权限仍需在实际设备验证。
- 本次验证：115 项单元 / 界面流程测试通过，Android Lint 无问题；APK 包名与标准调试签名已核验。

## 文档

- [课堂与动态 API](docs/classroom-activity-api.md)
- [课堂凭证存储](docs/local-storage.md)
- [动态缓存](docs/activity-cache.md)
- [User-Agent 与图片操作](docs/user-agent.md)
- [雨课堂接口说明](docs/rain-classroom-api.md)

## 许可

本仓库暂未附 LICENSE 文件；使用、再分发前请以作者后续声明为准。
