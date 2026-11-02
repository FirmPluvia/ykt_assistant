# 课堂动态与回顾接口（来自用户抓包，已脱敏）

补充原 API 文档。样本来自长江雨课堂，包含 6 组请求 / 响应。本文件仅描述结构与调用关系，不保存 Cookie、用户姓名、学号、真实 ID、头像或图片签名。

这些接口使用登录账号 Cookie。抓包没有课堂 Authorization Bearer；查看报告不需要先调用签到接口。路径按当前选定雨课堂服务拼接。

## 最近动态

`POST /api/v3/activities/received/`

```json
{"size":10,"type":[],"beginTime":null,"endTime":null}
```

响应 `code: 0`，`data` 为数组。字段：

| 字段 | 样本含义 |
| --- | --- |
| `id` | 动态记录 ID |
| `coursewareType` | 样本中全部为 `14`，表示课堂记录 |
| `coursewareId` | 此类型中为单次课堂 ID，与报告的 lessonId 对应 |
| `coursewareTitle` | 课堂标题 |
| `createTime` | 动态时间，样本为 Unix 毫秒 |
| `courseId` / `courseName` | 课程 |
| `classroomId` / `classroomName` | 班级 |
| `teacher` | 教师姓名 |
| `content` / `sort` | content 为类型相关内容；sort 原值作为下一页 searchAfter |

后续抓包已确认分页：下一页在同一路径增加 `searchAfter`，值为上一页原始响应最后一条记录的 `sort` 字符串。例如（脱敏示意）：

```json
{"size":10,"searchAfter":"[1760000000000,\"opaque-id\"]","type":[],"beginTime":null,"endTime":null}
```

`searchAfter` 是字符串，不是 JSON 数组；应用不解析 / 重建其内部时间或 ID。游标取自原始响应末条，不能从排序、过滤、课堂去重后的展示列表获取。页面合并后再做动态去重。

每次加载 10 条，可以继续加载更多；空响应表示结束。缺失 / 重复 / 循环游标会停止继续请求并提示刷新，避免死循环。加载失败保留列表与游标，重试同一页；刷新从无 searchAfter 的第一页重新开始。

新分页样本含 coursewareType=5 的作业通知。应用展示其摘要，但不把作业 ID 当作 lessonId，也不向课堂报告接口查询作业签到。

## 课堂信息

`GET /api/v3/classroom-report/student/lesson-info?lesson_id=<课堂ID>&front_time=<当前毫秒时间>`

`data` 包含 lessonId、lessonName、teacherName、classroomId、classroomName、courseId、courseName、start、end、presentationIds 等。样本的起止时间为毫秒。返回 lessonId 与请求不同时拒绝展示，避免关联错误。

## 学生课堂详情与签到

`GET /api/v3/classroom-report/student/detail?lesson_id=<课堂ID>&front_time=<当前毫秒时间>`

`data.detail` 包含：

| 字段 | 说明 |
| --- | --- |
| `valid` | 样本已签到记录为 1，其他值不推断完整枚举 |
| `checkInTime` | 签到毫秒时间 |
| `checkInName` | 样本为“扫二维码” |
| `checkInChannel` | 样本为“APP” |
| `checkinSource` / `checkinTagCode` | 保留，未猜测缺勤 / 迟到等额外状态 |

应用在 valid=1 且签到时间有效时显示“已签到”。按 2.4.1 的展示规则：课堂信息成功返回且结束时间已到、签到详情也成功返回但未确认签到时显示“未签到”；仍在上课 / 结束时间不明确时保留待确认，网络错误显示暂不可用。这个展示规则不是对官方全部签到枚举的推断。未确认记录不显示时间、方式、渠道占位项。

`data.presentation` 为课件摘要数组：presentationId、title、cover、totalCount、problemCount、showPresentation 等。totalCount 是报告中的原课件页数，不等于回顾页数。

## 回顾页面

`GET /api/v3/classroom-report/student/review?lesson_id=<课堂ID>&front_time=<当前毫秒时间>`

`data.timelineList` 含 type=slide 的翻页 / 动画记录。字段包括 id、presentationId、index、cover、visible、pres、si、sid、step、dt 等。

- 按课件 ID 分组，按页面 ID 去重，同页使用最近的事件。
- 去重后按 index / si 排序，保留“原第 N 页”信息，不把原页码当作数组索引。
- 不展示最新事件明确 visible=false 的页面，也不从更早事件恢复它。
- signed cover URL 按原值使用；通过账号重新请求回顾可取得更新地址。
- 抓包共有 63 条翻页事件，仅 24 个唯一页面，涉及 2 份共 65 页的原课件。说明回顾不等于完整原始 PPT。
- 应用可以预览和导出这些可见回顾页；未展示、未开放或未返回的页不补造。

## 同时捕获但本版未使用

- `/api/v3/classroom-report/lesson-extra`：digitalPen、vote、qrCodeState 等；没有把未知标志作为个人签到状态。
- `/api/v3/classroom-report/replay`：课堂、录像及 presentations 等；本次需求不实现视频回放，也不推断空 timeLine 能补齐全部页面。

## 夹具与验证

`android/app/src/test/resources/captured-history-redacted.json` 仅保留响应 JSON 结构。所有标识、姓名、头像、时间和图片签名均已替换；请求头与原始抓包不进入源码包或 APK。

回归测试覆盖最近动态关联、签到状态、重复动画去重、隐藏页、报告请求的 Cookie / Bearer 分离、界面列表 → 详情 → 回顾、退出清理，以及历史浏览不改变实时课堂凭证与翻页偏好。测试不使用抓包账号访问官方服务。

## 原版课件（2.4.4 修正）

官方课堂报告页面源码确认了历史课件专用接口，与需要课堂 Bearer 的实时 /api/v3/lesson/presentation/fetch 不同。

GET /api/v3/classroom-report/student/ppt?lesson_id=<课堂 ID>&presentationId=<课件 ID>

- lesson_id 为动态关联的课堂 ID；presentationId 使用 detail.presentation[].presentationId，注意驼峰命名。
- 认证使用当前账号 Cookie，不自动 checkin，不借用实时课堂 Bearer。
- 响应 data.slideList 为原课件页面数组，包含 id、index、cover、visible、hasProblem 等字段。官方学生页面只展示 visible 为真值的页面；客户端采用同样规则，不从时间线补页或补图。
- 兼容现有阅读和导出代码时映射成 slides；保持服务端原有顺序、图片地址和备注等字段。若没有学生可见页面则提示未开放，结构缺失 / 关联不符则报错。
- 查看、刷新、PPTX 与 ZIP 使用同一份数据。原版指原课件页面；导出 PPTX 仍是页面图片生成。
- 403 提示课件访问权限，401 才提示账号登录问题；不会回退到课堂回顾。

接口证据来自官方公开前端页面（2026-10-30 检查），未使用抓包中的账号访问课堂数据：

- https://changjiang.yuketang.cn/m/v2/lesson/student/0/overview
- https://fe-static-yuketang.yuketang.cn/fe/static/web/1.2.346/js/mobile.47ae9932.js：lessonSummary.get_stu_ppt 路径定义。
- https://fe-static-yuketang.yuketang.cn/fe/static/web/1.2.346/js/74403.86c7f593.js：courseware-detail 调用参数与 data.slideList / visible 使用方式。
- use-request 工具默认执行 GET，成功结果取 response.data。

测试模拟官方接口结构，覆盖请求参数、无实时 Bearer、完整可见页、隐藏页、刷新、权限失败、结构缺失和课堂 / 课件关联检查；真实账号访问仍待设备确认。

## 首页课堂入课（2.7.4）

首页点击正在上课课程不再调用 student/detail，也不拉取动态来判断签到状态。入课与动态报告展示完全分离：

1. 本机已持有同一课堂凭证：直接进入并保留阅读位置。
2. 否则在后台从本地加密缓存查找匹配账号、服务器和 lessonId 的凭证；缓存有效时恢复 Bearer / lessonToken 并直接连接实时课堂，不发送 checkin。
3. 缺失、过期、损坏或读取失败时提示“未找到课堂凭证，是否签到并进入课堂？”。确认前不产生签到或报告请求。
4. 用户确认后才调用 /api/v3/lesson/checkin 获取新凭证并保存；取消不入课。服务端动态扫码等规则保持有效。
5. 取消缓存读取、退出账号后，迟到读取结果不能恢复课堂状态或连接。

student/detail 仍用于动态中的历史课堂与签到记录查看，不作为首页入课的前置条件。没有缓存不等于服务端未签到，界面只提示缺少本机凭证，不推断实际签到状态。
