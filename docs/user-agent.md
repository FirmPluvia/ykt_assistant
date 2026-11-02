# User-Agent 与左右滑动（2.7.0）

## 默认 User-Agent

默认模板按用户提供的格式保留，仅由设备公开信息填充以下字段：

- Android 版本：Build.VERSION.RELEASE。
- 手机型号：Build.MODEL。
- Build 编号：Build.ID（系统构建标识，不是雨课堂客户端 buildnumber）。

```text
Mozilla/5.0 (Linux; Android <Android版本>; <手机型号> Build/<Build编号>; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/153.0.8010.36 Mobile Safari/537.36 flutterApp Mobile flutterApp
```

Chrome / AppleWebKit / Safari / Version 与 flutterApp 部分固定保留上述模板值，没有声称这些是手机实际安装的引擎版本。另将雨课堂 API 请求中的 brand 与 systemversion 头设置为设备 Build.BRAND（缺失时回退 MANUFACTURER）与 Android RELEASE。xtua / buildnumber 等服务协议字段保持兼容值，不用手机 Build.ID 替换。

无需额外设备权限。设备字段移除不能放入 HTTP 头的字符；不可用时采用明确的 unknown / Android Device 回退值，避免构造非法请求。

## 我的 → 请求设置 → 修改 User-Agent

- 可编辑完整 UA，保存后持久化在应用内部 SharedPreferences，退出账号不清除该应用偏好。
- “恢复设备默认”移除自定义值，重新读取当前设备信息生成默认 UA。
- 自定义值最多 2048 个字符，仅接受可打印 ASCII，禁止空值、回车、换行、制表符及其他控制字符，防止注入额外 HTTP 请求头。
- 新值立即用于后续 API 请求、二维码 / 课件图片请求，以及下一次 WebSocket 握手。已经发送的请求和已建立的 WebSocket 不会强制重启；重新连接课堂时使用新值。
- 修改 UA 不清除账号 Cookie、课堂凭证或缓存，不自动发起签到。服务端仍决定请求与凭证是否有效。

## 全屏左右滑动

点击课件图片进入全屏后，左滑下一张，右滑上一张；边界不循环。手势需要达到合理的水平距离，纵向拖动不翻页。

默认适配尺寸下可滑动翻页；放大时单指用于拖动图片，双指缩放、长按和双击不会同时触发翻页。恢复至适配尺寸后可继续滑动。实时课堂手动翻页会按原有规则关闭跟随教师，历史课件翻页不会修改实时课堂位置或跟随设置。

## 验证

测试覆盖默认模板、真实设备字段填充函数、非法头拒绝、HTTP / 资源 / WebSocket 请求头更新、设置保存 / 恢复、左右方向、边界、纵向 / 缩放冲突与历史 / 实时状态隔离。实际手机字段在“我的”页预览中可查看；没有使用真实账号验证服务端对自定义 UA 的兼容性。
