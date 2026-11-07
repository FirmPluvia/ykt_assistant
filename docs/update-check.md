# 更新检测（3.2）

GitHub 3.2 默认构建（`com.ykt.assistant`）与私有包使用同一有道云更新流程，无需额外开关或 GitHub Secrets：首次启动和每次返回前台静默检查，与自动登录 / 课程加载并行，不显示“正在检测更新”弹窗。只在配置 versionCode 高于本机时提示；forceUpdate=true 的新版本不可通过返回键、外部点击或稍后使用取消。

GET `https://share.note.youdao.com/yws/api/note/ddc0a3355abb04b3213b3bd2409a94da?sev=j1`，使用当前应用设置的 User-Agent 和设备 brand / systemversion，无雨课堂 Cookie / Bearer。

返回 JSON 的 content 按 HTML 转为文本，再提取全角 ［］或半角 [] 中的标准 Base64，UTF-8 解码为版本 JSON。更新链接只支持 HTTPS，网盘和 GitHub 链接独立。

保留原先的失败策略：无法获取有效更新配置时打开 `https://github.com/FirmPluvia/ykt_assistant` 并退出应用；不使用旧配置绕过失败。打开项目主页使用独立浏览器任务，移除应用任务不会关闭浏览器。单次检测最长约 15 秒，不并发重复请求。

当前配置示例见 `update-manifest.example.json`，笔记用完整文本见 `update-note.example.txt`。示例版本 3.2 / 31，forceUpdate=false，更新 URL 留空；同版本不弹更新。未来发布应提高 versionCode，不能只改 versionName。启用强制更新前请至少配置一个可用链接。

先确保有道笔记已保存有效的［Base64 JSON］，再安装启用该规则的应用；占位文字“［base64编码内容］”不是有效配置，会按上述规则退出。本次开发不会写入远程笔记。

## GitHub 构建与发布

GitHub Actions 默认打包版本为 3.2 / 31，更新接口不因调试签名或包名而关闭。当前笔记是共享更新源，网盘 / GitHub 链接应指向适用安装包；不同包名或签名不能直接覆盖安装。仓库内示例不会自动上传到有道笔记，发布前需手动同步笔记内容。工作流、签名密钥及 Secrets 未修改。
