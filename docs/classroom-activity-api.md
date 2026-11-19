# 课堂动态与课件接口

本文补充[雨课堂 API](rain-classroom-api.md)，汇总来自长江雨课堂的脱敏样本和当前应用使用的接口。

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
| `coursewareType` | `14` 表示课堂记录，`5` 表示作业 |
| `coursewareId` | 课堂记录中为单次课堂 ID，与报告的 lessonId 对应 |
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

应用在 valid=1 且签到时间有效时显示“已签到”。当前展示规则如下。课堂信息成功返回且结束时间已到、签到详情也成功返回但未确认签到时显示“未签到”；仍在上课 / 结束时间不明确时保留待确认，网络错误显示暂不可用。这个展示规则不是对官方全部签到枚举的推断。未确认记录不显示时间、方式、渠道占位项。

`data.presentation` 为课件摘要数组：presentationId、title、cover、totalCount、problemCount、showPresentation 等。totalCount 是报告中的原课件页数。

## 夹具与验证

`android/app/src/test/resources/captured-history-redacted.json` 仅保留响应 JSON 结构。所有标识、姓名、头像、时间和图片签名均已替换；请求头与原始抓包不进入源码包或 APK。

回归测试覆盖动态和课堂关联、签到状态、原版课件可见页、报告请求认证及实时课堂凭证隔离。

## 原版课件

官方课堂报告页面源码确认了历史课件专用接口，与需要课堂 Bearer 的实时 /api/v3/lesson/presentation/fetch 不同。

GET /api/v3/classroom-report/student/ppt?lesson_id=<课堂 ID>&presentationId=<课件 ID>

- lesson_id 为动态关联的课堂 ID；presentationId 使用 detail.presentation[].presentationId，注意驼峰命名。
- 认证使用当前账号 Cookie，不自动 checkin，不借用实时课堂 Bearer。
- 响应 data.slideList 为原课件页面数组，包含 id、index、cover、visible、hasProblem 等字段。官方学生页面只展示 visible 为真值的页面；客户端采用同样规则，不从时间线补页或补图。
- 兼容现有阅读和导出代码时映射成 slides；保持服务端原有顺序、图片地址和备注等字段。若没有学生可见页面则提示未开放，结构缺失 / 关联不符则报错。
- 查看、刷新、PPTX 与 ZIP 使用同一份数据。原版指原课件页面；导出 PPTX 仍是页面图片生成。
- 403 提示课件访问权限，401 才提示账号登录问题。

接口证据来自官方公开前端页面（2026-10-30 检查），未使用抓包中的账号访问课堂数据：

- https://changjiang.yuketang.cn/m/v2/lesson/student/0/overview
- https://fe-static-yuketang.yuketang.cn/fe/static/web/1.2.346/js/mobile.47ae9932.js：lessonSummary.get_stu_ppt 路径定义。
- https://fe-static-yuketang.yuketang.cn/fe/static/web/1.2.346/js/74403.86c7f593.js：courseware-detail 调用参数与 data.slideList / visible 使用方式。
- use-request 工具默认执行 GET，成功结果取 response.data。

测试模拟官方接口结构，覆盖请求参数、无实时 Bearer、完整可见页、隐藏页、刷新、权限失败、结构缺失和课堂 / 课件关联检查；真实账号访问仍待设备确认。

## 首页课堂入课

首页点击正在上课课程不再调用 student/detail，也不拉取动态来判断签到状态。入课与动态报告展示完全分离：

1. 本机已持有同一课堂凭证：直接进入并保留阅读位置。
2. 否则在后台从本地加密缓存查找匹配账号、服务器和 lessonId 的凭证；缓存有效时恢复 Bearer / lessonToken 并直接连接实时课堂，不发送 checkin。
3. 缺失、过期、损坏或读取失败时提示“未找到课堂凭证，是否签到并进入课堂？”。确认前不产生签到或报告请求。
4. 用户确认后才调用 /api/v3/lesson/checkin 获取新凭证并保存；取消不入课。服务端动态扫码等规则保持有效。
5. 取消缓存读取、退出账号后，迟到读取结果不能恢复课堂状态或连接。

student/detail 仍用于动态中的历史课堂与签到记录查看，不作为首页入课的前置条件。没有缓存不等于服务端未签到，界面只提示缺少本机凭证，不推断实际签到状态。

## 课堂 ID 入课

正在上课卡片可复制 `onLessonClassrooms[].lessonId`。首页手动输入课堂 ID 使用相同的凭证复用 / 确认签到流程，不依赖动态或签到报告查询。详见[课堂 ID 与手动入课](lesson-id-entry.md)。

## 实时动态与多账号

正在上课的动态记录在本机凭证命中时使用实时 presentation/fetch 接口，不使用历史 ppt 接口替代。账号选择、逐账号 checkin 与取消语义见[多账号说明](multiple-accounts.md)。
