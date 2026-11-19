# Android 源码

当前正式版 **4.0 / 60**，默认包名 `com.ykt.assistant`，运行于 Android 8.0 及以上版本。

本目录包含 Android 应用、Gradle Wrapper 和测试。使用 JDK 17、Android SDK 35 与 Build Tools 35.0.0：

```sh
sh ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

调试 APK 输出至 `app/build/outputs/apk/debug/app-debug.apk`。

功能与使用入口见[项目 README](../README.md)。
