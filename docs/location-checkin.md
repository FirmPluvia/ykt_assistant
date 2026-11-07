# 服务端位置签到（3.2）

根据前述约定重新实现，采用雨课堂返回的签到中心坐标，不读取手机 GPS / 网络定位，已删除精确和大致定位权限。该坐标代表服务端签到位置，不是手机实测位置。只处理当前已进入的课堂，保留用户确认，不自动批量签到。

## 流程

GET `/api/v3/lesson/location-checkin/eligibility` 查询状态，携带当前课堂 Bearer。已签到直接提示；未开始、已结束、状态不完整或缺少坐标时不提交。未签到且可用时显示坐标和地址，点击“确认位置签到”后重新查询资格与目标，仍匹配才 POST `/api/v3/lesson/location-checkin/submit`。

提交字段：lat、lng、当前 lessonId、source=54、服务端 address。POST 的 code=0 只表示接受提交，再次查询返回 signed=true 才提示签到成功；否则保留“刷新状态”和等待确认提示。

## 数据和保护

优先读取 session.centerLat / centerLng，其次完整的 lat / lng，最后读取当前课堂 locationcheckinext 事件中的 centerLat / centerLng。支持 data 包装、数值字符串；每个来源必须是完整坐标对，拒绝跨来源拼接、空值、布尔值、NaN、Infinity 或越界值。

提交串行化并绑定课堂 ID / Bearer / 轮次；发生换课、结束、新轮次或坐标变化时旧确认失效。本轮相同目标 POST 成功后只查状态，即使后续查询失败也不自动重复提交。位置 POST 客户端关闭连接失败自动重试。

locationcheckinext 只提示开启，不直接提交；locationcheckinend 或下课使旧事件失效并关闭旧确认。重连、退出会清除内存状态。日志只记录 LOCATION 的阶段和错误码，不记录坐标或地址。

测试覆盖坐标来源、非法数据、提交字段、已签到跳过、轮次 / 换课失效、防重复、确认失败、查询失败恢复、注销隔离及无定位权限。测试不访问真实课堂，最终状态仍以雨课堂接口为准。
