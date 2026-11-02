# 雨课堂 API 参考

包含登录、课程、签到、课件、答题、图片上传的 21 个 HTTP 接口及课堂 WebSocket 协议。

响应示例为字段子集，示例 ID、时间、URL 和文本为占位数据，尚未经真实账号联调。未确认的字段或返回结构标为“待确认”；参数表描述传值规则，不额外推断服务端强制必填条件。

## 1. 通用约定

### 1.1 服务地址

| 服务 | BASE_URL | WebSocket URL |
| --- | --- | --- |
| 雨课堂 | `https://www.yuketang.cn` | `wss://www.yuketang.cn/wsapp/` |
| 荷塘雨课堂 | `https://pro.yuketang.cn` | `wss://pro.yuketang.cn/wsapp/` |
| 长江雨课堂 | `https://changjiang.yuketang.cn` | `wss://changjiang.yuketang.cn/wsapp/` |
| 黄河雨课堂 | `https://huanghe.yuketang.cn` | `wss://huanghe.yuketang.cn/wsapp/` |

相对路径均以选定服务的 BASE_URL 为前缀。微信授权和七牛上传使用各自独立域名。

### 1.2 请求格式

- 普通 POST 请求体为 JSON，可使用 `Content-Type: application/json`。
- 文件上传使用 `multipart/form-data`，边界由请求工具生成。
- GET 参数放入查询字符串，参数值需进行 URL 编码。
- 默认响应为 JSON；微信授权还包含 XML、JavaScript 文本和重定向响应。
- HTTP 头名称不区分大小写；JSON 字段和 WebSocket 消息类型按原始大小写使用。

公共请求头示例：

```http
user-agent: Android
brand: google Pixel 9 Pro
uuid:
buildnumber: 1610
xtua: client=app&tag=1.3.3&platform=Android
systemversion: 16
incremental: 14624737
accept: application/json
isphysicaldevice: true
xtbz: ykt
x-client: app
```

以上为请求配置示例，不表示每个字段及设备取值均为服务端强制要求。示例中的空 `uuid` 与登录请求的 `pushDeviceId` 是不同字段。

### 1.3 认证

| 认证上下文 | 使用方式 | 场景 |
| --- | --- | --- |
| 临时会话 | 保留同一轮登录产生的 Cookie | 验证码、密码、二维码及微信授权流程 |
| 账号会话 | 携带目标账号在对应域名下的 Cookie | 用户资料、课程、签到入课 |
| 课堂 Bearer | 账号会话加 `authorization: Bearer <bearerToken>` | 位置签到、课件、答题 |
| WebSocket 课堂认证 | `hello.auth` 使用 `lessonToken` | 课堂实时连接 |
| 七牛上传认证 | multipart 表单中的 `token` | 图片上传 |

存在对应 Cookie 时的请求头示例：

```http
Cookie: sessionid=<会话ID>; csrftoken=<CSRF令牌>
x-csrftoken: <csrftoken>
x-uid: <用户ID>
sessionid: <会话ID>
```

`x-csrftoken`、`sessionid` 分别对应同名 Cookie。尚未取得用户 ID 的临时会话不能预填其他用户 ID。Cookie 仅发送至匹配的域名，各账号分别维护认证上下文。

| 凭证 | 来源 | 用途 |
| --- | --- | --- |
| 账号 Cookie | 登录响应 `Set-Cookie`（若返回） | 保持登录状态 |
| 二维码登录 token | A05 的 `data.token` | A06 等待扫码授权 |
| Bearer token | A13 响应头 `set-auth` | 课堂 HTTP 接口 |
| lessonToken | A13 的 `data.lessonToken` | WebSocket `hello.auth` |
| 七牛上传 token | A20 的 `data.token` | A21 文件上传 |

这些凭证不能互换。课堂凭证应与请求账号和目标课堂保持一致。

### 1.4 响应和时间

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `code` | integer | 业务状态码，采用该字段的接口通常以 `0` 表示成功，与 HTTP 状态码不同 |
| `msg` | string，若返回 | 服务端提示或失败原因，不保证每个响应都包含 |
| `data` | object / array | 业务数据，各接口结构不同 |
| `success` | boolean | 上传相关的成功标志，不能与 `code` 混用 |
| 答题及事件 `dt` | integer | Unix 毫秒时间，不使用 Unix 秒数或日期字符串 |
| `startMs` / `serverTime` / `endedAt` / `endMs` | integer，待确认 | 位置签到事件时间，预期为毫秒 |
| `limit` / `extend` | integer | 秒；作答时限 / 增加的时长 |

`{"code": 0}` 仅表示成功响应中已确认的字段子集，不表示真实响应一定没有其他字段。HTTP 2xx 不等于业务成功；网络异常、响应无法解析与业务失败应分别处理。

未确认原始类型的 ID 可按原值接收，关联比较时可统一转成字符串，避免大整数精度损失。`course_id/courseId` 为课程 ID，`classroom_id` 为班级/课堂组织 ID，`lessonId/lessonid` 为单次课堂 ID，不应互相代替。

## 2. HTTP 接口索引


| 编号 | 方法 | 地址 | 功能 |
| --- | --- | --- | --- |
| [A01](#a01) | POST | `/api/v3/user/code/send` | 发送短信验证码 |
| [A02](#a02) | POST | `/api/v3/user/code/verify` | 校验短信验证码 |
| [A03](#a03) | POST | `/api/v3/user/login/app` | 密码或短信登录 |
| [A04](#a04) | GET | `/v/course_meta/user_info` | 获取用户信息 |
| [A05](#a05) | GET | `/api/v3/user/login/pre-info` | 获取登录二维码 |
| [A06](#a06) | POST | `/api/v3/user/login` | 等待二维码登录授权 |
| [A07](#a07) | POST | `/api/v3/user/login/wechat-auth-param` | 获取微信网页授权参数 |
| [A08](#a08) | GET | `https://open.weixin.qq.com/connect/qrconnect` | 获取微信二维码 UUID |
| [A09](#a09) | GET | `https://lp.open.weixin.qq.com/connect/l/qrconnect` | 查询微信扫码授权状态 |
| [A10](#a10) | GET | `/api/v3/user/login/wechat-web-callback` | 微信网页授权回调 |
| [A11](#a11) | GET | `/v/course_meta/learning_list/` | 获取课程资料列表 |
| [A12](#a12) | GET | `/api/v3/classroom/on-lesson` | 获取正在上课的课堂 |
| [A13](#a13) | POST | `/api/v3/lesson/checkin` | 签到入课并获取课堂凭证 |
| [A14](#a14) | POST | `/api/v3/app/scan` | 解析动态签到二维码 |
| [A15](#a15) | GET | `/api/v3/lesson/location-checkin/eligibility` | 查询位置签到状态 |
| [A16](#a16) | POST | `/api/v3/lesson/location-checkin/submit` | 提交位置签到 |
| [A17](#a17) | GET | `/api/v3/lesson/presentation/fetch` | 获取整份课件 |
| [A18](#a18) | POST | `/api/v3/lesson/problem/answer` | 提交课堂答案 |
| [A19](#a19) | POST | `/api/v3/lesson/problem/retry` | 补交课堂答案 |
| [A20](#a20) | POST | `/pc/generate_qiniu_token` | 获取七牛上传 token |
| [A21](#a21) | POST | `https://upload.qiniup.com/` | 上传图片到七牛 |

## 3. 登录接口

<a id="a01"></a>

### A01. 发送短信验证码

`POST /api/v3/user/code/send`

认证：临时会话、腾讯验证凭证。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `phoneNumber` | string | 手机号 |
| `email` | string | 发送空字符串 |
| `ticket` | string | 腾讯人机验证成功后的 ticket |
| `rand` | string | 腾讯人机验证返回的 randstr |

```json
{
  "phoneNumber": "<手机号>",
  "email": "",
  "ticket": "<腾讯验证ticket>",
  "rand": "<腾讯验证randstr>"
}
```

**预期响应与含义**

```json
{
  "code": 0
}
```

`code: 0` 表示验证码发送请求成功，不表示账号已登录。非零时读取 `msg`（若返回）获取原因，具体失败码待确认。

<a id="a02"></a>

### A02. 校验短信验证码

`POST /api/v3/user/code/verify`

认证：临时会话。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `phoneNumber` | string | 接收验证码的手机号 |
| `email` | string | 发送空字符串 |
| `code` | string | 短信验证码，保留可能存在的前导零 |

```json
{
  "phoneNumber": "<手机号>",
  "email": "",
  "code": "<短信验证码>"
}
```

**预期响应与含义**

```json
{
  "code": 0
}
```

`code: 0` 表示校验通过，可继续 A03；验证码校验本身不等于登录完成。失败提示为 `msg`（若返回）。

<a id="a03"></a>

### A03. 密码或短信登录

`POST /api/v3/user/login/app`

认证：临时会话。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `type` | integer | `1` 手机号密码，`2` 邮箱密码，`3` 手机号短信验证码 |
| `phoneNumber` | string | 手机号模式填写，其他模式为空字符串 |
| `password` | string | 密码模式填写，短信模式为空字符串 |
| `email` | string | 邮箱密码模式填写，其他模式为空字符串 |
| `code` | string | 短信模式填写，其他模式为空字符串 |
| `pushDeviceId` | string | 设备标识 |
| `ticket` | string | 腾讯验证 ticket |
| `rand` | string | 腾讯验证随机串 |

```json
{
  "type": 1,
  "phoneNumber": "<手机号>",
  "password": "<密码>",
  "email": "",
  "code": "",
  "pushDeviceId": "<本地设备标识>",
  "ticket": "<腾讯验证码ticket>",
  "rand": "<腾讯验证码随机串>"
}
```

上例为手机号密码登录。邮箱密码模式使用 `type: 2` 并填写 `email`、`password`；短信模式使用 `type: 3` 并填写 `phoneNumber`、`code`。短信登录先执行 A01 和 A02。

**预期响应与含义**

```json
{
  "code": 0
}
```

`code: 0` 表示登录操作成功。保留响应的 `Set-Cookie`（若返回），随后调用 A04 获取用户资料。其他登录数据结构待确认，不假定存在独立的账号 access token；失败时可读取 `msg`。

<a id="a04"></a>

### A04. 获取用户信息

`GET /v/course_meta/user_info`

认证：已登录的临时会话或账号会话。

无显式查询参数或请求体。

**预期响应与含义**

```json
{
  "data": {
    "user_profile": {
      "user_id": "<用户ID>",
      "name": "示例用户",
      "avatar": "https://example.com/avatar.jpg",
      "avatar_96": "https://example.com/default-avatar.jpg",
      "phone_number": "<手机号>",
      "school": "示例学校"
    }
  }
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `data.user_profile` | object | 用户资料对象 |
| `user_id` | string / number | 用户 ID，后续用于 x-uid 和 WebSocket userid |
| `name` | string | 姓名 |
| `avatar` | string | 头像地址 |
| `avatar_96` | string | 备用头像地址 |
| `phone_number` | string | 手机号 |
| `school` | string | 学校名称 |

表中除 `data.user_profile` 外，字段均位于该对象内。用户资料为空或缺失时不能认定取得有效账号资料。完整业务状态码外壳待确认。

<a id="a05"></a>

### A05. 获取登录二维码

`GET /api/v3/user/login/pre-info`

认证：临时会话。

无显式查询参数或请求体。

**预期响应与含义**

```json
{
  "code": 0,
  "data": {
    "qrImage": "<服务端返回的二维码图片地址>",
    "token": "<本轮二维码登录token>"
  }
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `code` | integer | 0 表示获取成功 |
| `data.qrImage` | string | 登录二维码图片地址 |
| `data.token` | string | 本轮扫码授权 token，传给 A06 |
| `data.qrContent` | 待确认 | 二维码内容字段，返回情况及格式待确认 |

二维码刷新后，A06 应使用对应新一轮的 token。二维码登录 token 与课堂 lessonToken 不同。

<a id="a06"></a>

### A06. 等待二维码登录授权

`POST /api/v3/user/login`

认证：临时会话、二维码 token。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `token` | string | A05 的 data.token |

```json
{
  "token": "<A05返回的token>"
}
```

使用长轮询，等待窗口约 30 秒，接收超时应覆盖该时间，例如 31 秒。

**预期响应与含义**

成功形态示意；`data` 内部结构待确认，空对象仅作非 null 对象占位。

```json
{
  "code": 0,
  "data": {}
}
```

`code: 0` 表示本次授权成功，随后通过会话 Cookie 调用 A04。超时示例：

```json
{
  "code": 50001
}
```

`50001` 表示扫码等待超时，可重新通过 A05 获取二维码。

<a id="a07"></a>

### A07. 获取微信网页授权参数

`POST /api/v3/user/login/wechat-auth-param`

认证：临时会话。

```json
{}
```

**预期响应与含义**

```json
{
  "data": {
    "appId": "<微信应用ID>",
    "redirectUri": "<服务端返回的授权回调地址>",
    "state": "<本轮授权state>"
  }
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `data.appId` | string | 微信应用 ID，传给 A08 |
| `data.redirectUri` | string | 用于构建微信授权回调地址 |
| `data.state` | string | 本轮授权关联值，后续按原值传递 |

完整业务状态码结构待确认。

<a id="a08"></a>

### A08. 获取微信二维码 UUID

`GET https://open.weixin.qq.com/connect/qrconnect`

认证：微信授权参数。

**查询参数**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `appid` | string | A07 的 data.appId |
| `scope` | string | snsapi_login |
| `redirect_uri` | string | A07 的 redirectUri 拼接 ?path=%2Fauthorize%2Fwx-qrlogin%3Fsuccess%3D1 |
| `state` | string | A07 的 data.state |
| `login_type` | string | jssdk |
| `self_redirect` | string | true |
| `f` | string | xml |

表中为参数值，写入查询字符串时仍需由请求工具编码。响应按 XML 文本接收。

**预期响应与含义**

```xml
<uuid><![CDATA[WECHAT_QR_UUID]]></uuid>
```

上例为响应 XML 中的关键片段。`uuid` 的 CDATA 文本是微信二维码 UUID，用于 A09；与 A07 的 state 和 A05 的登录 token 不同。完整 XML 外壳及其他状态字段待确认。

<a id="a09"></a>

### A09. 查询微信扫码授权状态

`GET https://lp.open.weixin.qq.com/connect/l/qrconnect`

认证：微信二维码 UUID。

**查询参数**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `uuid` | string | A08 返回的微信二维码 UUID |

响应是包含 JavaScript 赋值片段的文本，不按 JSON 解析。请求可能等待授权状态变化，接收超时应考虑长等待。

**预期响应与含义**

```javascript
window.wx_errcode=405;
window.wx_code='WECHAT_AUTH_CODE';
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `window.wx_errcode` | 数字文本 | 405 表示微信授权完成，其他取值含义待确认 |
| `window.wx_code` | string | 微信授权 code，传给 A10 |

微信授权完成后还需取得有效 code 并执行 A10，不能只凭 405 认定雨课堂登录已完成。

<a id="a10"></a>

### A10. 微信网页授权回调

`GET /api/v3/user/login/wechat-web-callback`

认证：临时会话、code/state。

**查询参数**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `path` | string | /authorize/wx-qrlogin?success=1 |
| `code` | string | A09 的 wx_code |
| `state` | string | A07 的 data.state |

**预期响应与含义**

可能通过重定向完成授权，以上下游实际响应为准；目标头示意如下。

```http
Location: /authorize/wx-qrlogin?success=1
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `Location` | 响应头，若返回 | 后续跳转地址，具体 HTTP 状态码和绝对/相对地址形式待确认 |
| `Set-Cookie` | 响应头，若返回 | 会话凭证，需要保留 |
| 正文 | 文本 | 具体结构待确认，不定义固定 JSON 外壳 |

完成重定向后调用 A04 检查用户资料。URL 中的 success=1 不可单独代替会话有效性检查。

## 4. 课程与签到接口

<a id="a11"></a>

### A11. 获取课程资料列表

`GET /v/course_meta/learning_list/`

认证：账号会话。

无显式查询参数或请求体。

**预期响应与含义**

```json
{
  "data": [
    {
      "course_id": "<课程ID>",
      "classroom_id": "<班级ID>",
      "course_name": "示例课程",
      "classroom_name": "示例班级",
      "teacher": {
        "name": "示例教师",
        "avatar": "https://example.com/teacher.jpg"
      }
    }
  ]
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `data` | array | 课程资料列表 |
| `data[].course_id` | string / number | 课程 ID，关联 A12 的 courseId |
| `data[].classroom_id` | string / number | 班级/课堂组织 ID，不是单次课堂 lessonId |
| `data[].course_name` | string | 课程名 |
| `data[].classroom_name` | string | 班级名 |
| `data[].teacher.name` | string | 教师姓名 |
| `data[].teacher.avatar` | string | 教师头像地址 |

`data: []` 表示无课程项。保留请求路径末尾 `/`；完整业务状态码外壳待确认。

<a id="a12"></a>

### A12. 获取正在上课的课堂

`GET /api/v3/classroom/on-lesson`

认证：账号会话。

无显式查询参数或请求体。

**预期响应与含义**

```json
{
  "code": 0,
  "data": {
    "onLessonClassrooms": [
      {
        "courseId": "<课程ID>",
        "lessonId": "<本次课堂ID>"
      }
    ]
  }
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `code` | integer | 0 表示查询成功 |
| `data.onLessonClassrooms` | array | 正在上课的课堂列表 |
| `data.onLessonClassrooms[].courseId` | string | 课程 ID，用于关联 A11 |
| `data.onLessonClassrooms[].lessonId` | string | 本次课堂 ID，传给 A13 |

`onLessonClassrooms: []` 表示成功查询但无正在上课的课堂，与业务失败或网络异常不同。

<a id="a13"></a>

### A13. 签到入课并获取课堂凭证

`POST /api/v3/lesson/checkin`

认证：账号会话。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `source` | integer | 发送 21，完整来源枚举待确认 |
| `lessonId` | string | A12 或 A14 返回的本次课堂 ID |
| `joinIfNotIn` | boolean | 发送 true，完整服务端加入规则待确认 |

```json
{
  "source": 21,
  "lessonId": "<本次课堂ID>",
  "joinIfNotIn": true
}
```

**预期响应与含义**

```http
set-auth: <bearerToken>
```

```json
{
  "code": 0,
  "data": {
    "lessonToken": "<课堂WebSocket凭证>"
  }
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `code` | integer | 0 表示本次签到入课成功 |
| `set-auth` | string，响应头 | 课堂 HTTP Bearer 凭证值 |
| `data.lessonToken` | string | WebSocket hello.auth 使用的课堂凭证 |

后续 HTTP 请求头为 `authorization: Bearer <set-auth的值>`。应同时取得两项课堂凭证，不仅检查 code。

要求动态二维码时的已知失败响应：

```json
{
  "code": 50070
}
```

`50070` 表示需要先执行 A14。普通课堂可直接使用 A12 的 lessonId 调用本接口；位置签到状态由 A15 独立确认。

<a id="a14"></a>

### A14. 解析动态签到二维码

`POST /api/v3/app/scan`

认证：账号会话。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `url` | string | 扫描取得的完整动态签到 URL，保留查询参数 |

```json
{
  "url": "<扫描得到的完整动态签到URL>"
}
```

动态 URL 识别形式为 `https://<服务主机>/api/v3/lesson/check-in/dynamic-qr-code...`。动态参数的完整定义待确认，按取得的完整 URL 提交。

**预期响应与含义**

```json
{
  "code": 0,
  "data": {
    "value": "<lessonId>"
  }
}
```

`code: 0` 表示二维码解析成功；`data.value` 为随后传给 A13 的 lessonId，不是课堂 token。解析成功本身不代表整条签到流程已完成。

扫码签到流程中的已知错误码：

```json
{
  "code": 51203
}
```

`51203` 按动态二维码过期处理，具体由解析还是后续入课步骤返回需结合实际响应确认。

<a id="a15"></a>

### A15. 查询位置签到状态

`GET /api/v3/lesson/location-checkin/eligibility`

认证：账号会话和课堂 Bearer。

无显式查询参数或请求体。

**预期响应与含义**

基础响应形态及可空性待确认；字段按顶层结构描述，不统一包在 data 中。

```json
{
  "needLocationSignIn": false,
  "signed": true,
  "mode": "signed",
  "checkInTime": 1700000000000,
  "address": ""
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `needLocationSignIn` | boolean | 是否需要位置签到的标志，具体语义待确认 |
| `signed` | boolean | true 表示已签到 |
| `mode` | string | 状态模式，已知示例为 signed，其他枚举待确认 |
| `checkInTime` | number | 签到时间，预期为毫秒时间戳 |
| `address` | string | 地址信息 |
| `session.centerLat` / `session.centerLng` | number，若返回 | 中心纬度 / 经度候选字段，结构待确认 |
| `lat` / `lng` | number，若返回 | 顶层坐标候选字段，结构待确认 |

请求不额外传 lessonId。signed 不为 true 不等于已确认允许签到；位置签到需求、会话有效性和请求失败应分别判断。

<a id="a16"></a>

### A16. 提交位置签到

`POST /api/v3/lesson/location-checkin/submit`

认证：账号会话和课堂 Bearer。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `lat` | number | 纬度 |
| `lng` | number | 经度 |
| `lessonId` | string | 本次课堂 ID |
| `source` | integer | 发送 54，完整来源枚举待确认 |
| `address` | string | 地址，无地址时为空字符串 |

```json
{
  "lat": 31.2304,
  "lng": 121.4737,
  "lessonId": "<本次课堂ID>",
  "source": 54,
  "address": "<地址>"
}
```

坐标示例仅演示数值格式；坐标系、允许距离及其他校验条件待确认。

**预期响应与含义**

```json
{
  "code": 0
}
```

`code: 0` 表示提交成功。签到时间、距离等其他返回字段待确认；需要最终状态时可再次调用 A15。

## 5. 课件与答题接口

<a id="a17"></a>

### A17. 获取整份课件

`GET /api/v3/lesson/presentation/fetch`

认证：账号会话和课堂 Bearer。

**查询参数**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `presentation_id` | string | WebSocket 返回的课件 ID |

**预期响应与含义**

```json
{
  "data": {
    "title": "示例课件",
    "width": 1280,
    "height": 720,
    "version": "1.0",
    "slides": [
      {
        "id": "<页面ID>",
        "index": 0,
        "cover": "https://example.com/slide.jpg",
        "coverAlt": "https://example.com/slide-large.jpg",
        "thumbnail": "https://example.com/slide-small.jpg",
        "shapes": [],
        "note": "",
        "problem": {
          "problemId": "<题目ID>",
          "problemType": 1,
          "body": "示例题目：请选择一个选项",
          "score": 100,
          "remark": "",
          "answers": [],
          "hasRemark": false,
          "options": [
            {"key": "A", "value": "选项一"},
            {"key": "B", "value": "选项二"}
          ],
          "blanks": []
        }
      }
    ]
  }
}
```

`data` 为整份课件，字段见第 7 节。普通页的 problem 可为空。示例 index: 0 仅演示类型，不统一定义其他消息的页码起点。coverAlt 等字段是页面图片 URL，不是原始 pptx 文件；answers 和 remark 是否返回及具体内容以实际权限和响应为准。

<a id="a18"></a>

### A18. 提交课堂答案

`POST /api/v3/lesson/problem/answer`

认证：账号会话和课堂 Bearer。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `problemId` | string | 题目 ID |
| `dt` | integer | Unix 毫秒时间，正常提交使用当前时间 |
| `problemType` | integer | 原始题型编号 |
| `result` | array / object | 按题型组织的答案 |

```json
{
  "problemId": "<题目ID>",
  "dt": 1700000000000,
  "problemType": 1,
  "result": ["A"]
}
```

| 题型值 | 题型 | result 形式 |
| --- | --- | --- |
| `1` | 单选 | 选项 key 数组，如 ["A"] |
| `2` | 多选 | 选项 key 数组，如 ["A", "C"] |
| `3` | 投票 | 选项 key 数组，可选数量结合题目限制 |
| `4` | 填空 | 按空位顺序排列的字符串数组 |
| `5` | 主观题 | content、pics、videos 对象 |
| `6` | 判断题编号 | 独立提交格式待确认；题目实际返回类型 1 时按单选提交 |

主观题 result 示例：

```json
{
  "content": "<用户输入的文字>",
  "pics": [
    {
      "pic": "<图片URL>",
      "thumb": "<图片URL>?imageView2/2/w/568"
    }
  ],
  "videos": []
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `result.content` | string | 文字答案，无文字时为空字符串 |
| `result.pics` | array | 图片对象列表，无图片时可发送空数组，接受条件以服务端为准 |
| `result.pics[].pic` | string | 上传后的图片完整 URL |
| `result.pics[].thumb` | string | 缩略图 URL |
| `result.videos` | array | 示例发送空数组，视频对象结构待确认 |

选项答案使用 options[].key，不用显示文字代替；填空答案保持空位顺序。

**预期响应与含义**

```json
{
  "code": 0
}
```

`code: 0` 表示答案提交成功，不表示答案正确或已经获得满分。判题、得分、正确答案的响应结构待确认。非零业务结果可读取 msg（若返回）。

<a id="a19"></a>

### A19. 补交课堂答案

`POST /api/v3/lesson/problem/retry`

认证：账号会话和课堂 Bearer。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `problems` | array | 待补交题目数组，示例单次提交一题 |
| `problems[].problemId` | string | 题目 ID |
| `problems[].dt` | integer | 题目开放事件或时间线的毫秒时间 |
| `problems[].problemType` | integer | 原始题型编号 |
| `problems[].result` | array / object | 格式同 A18 对应题型 |
| `problems[].retry_times` | null | 发送 JSON null，不是字符串 null |

```json
{
  "problems": [
    {
      "problemId": "<题目ID>",
      "dt": 1700000000000,
      "problemType": 1,
      "result": ["A"],
      "retry_times": null
    }
  ]
}
```

补交有 problems 外层；补交窗口、时间字段校验和单次题目数量上限待确认，不能推断支持无限期或任意批量补交。

**预期响应与含义**

```json
{
  "code": 0
}
```

`code: 0` 表示本次补交操作成功，不等于答案正确。逐题结果和分数结构待确认；非零业务结果可读取 msg（若返回）。

## 6. 上传接口

<a id="a20"></a>

### A20. 获取七牛上传 token

`POST /pc/generate_qiniu_token`

认证：账号会话。

**请求参数：JSON**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `bucket_name` | string | cms-attachment |
| `expired_time` | integer | 3600，预期单位为秒 |

```json
{
  "bucket_name": "cms-attachment",
  "expired_time": 3600
}
```

**预期响应与含义**

```json
{
  "success": true,
  "data": {
    "token": "<七牛上传token>"
  }
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `success` | boolean | true 表示获取上传凭证成功 |
| `data.token` | string | A21 表单使用的上传 token |

实际授权有效期及过期时间字段待确认；请求参数 3600 不可直接当成服务端承诺的实际有效期。

<a id="a21"></a>

### A21. 上传图片到七牛

`POST https://upload.qiniup.com/`

认证：七牛上传 token。

**请求参数：multipart/form-data**

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `file` | binary | 图片文件，multipart 文件名可使用生成的资源文件名 |
| `token` | string | A20 返回的 data.token |
| `key` | string | 资源键，示例为毫秒时间戳字符串与原始文件名直接拼接 |
| `fname` | string | 原始文件名 |

**预期响应与含义**

以下为待联调的兼容响应形态，尤其需要确认顶层 success 是否始终返回。

```json
{
  "success": true,
  "key": "1700000000000example.jpg"
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `success` | boolean，待确认 | 上传成功标志 |
| `key` | string | 上传对象的资源键，不是完整 URL |

例如 key 为 1700000000000example.jpg，对应图片 URL 为 `https://qn-scd1.yuketang.cn/1700000000000example.jpg`。完整 URL 用于 A18/A19 的 result.pics[].pic；缩略图可追加 `?imageView2/2/w/568`。

### 6.1 资源读取

| 来源 | 请求形式 | 内容 |
| --- | --- | --- |
| A05 的 qrImage | GET 服务端返回的地址 | 登录二维码图片 |
| A17 的 cover / coverAlt / thumbnail | GET 对应地址 | 课件图片或缩略图 |
| 七牛资源 | GET https://qn-scd1.yuketang.cn/<key> | 上传的图片 |

图片请求不使用业务接口的 JSON 成功外壳。资源认证要求、URL 有效期及 MIME 类型以对应响应为准。

## 7. 课件与题目数据结构

### 7.1 课件对象：data

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `title` | string | 标题 |
| `width` / `height` | integer | 画布宽度 / 高度 |
| `version` | string | 数据版本 |
| `slides` | array | 全部页面 |

### 7.2 页面对象：slides[]

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `id` | string | 页面 ID |
| `index` | integer | 页序，起点以实际响应为准 |
| `cover` / `coverAlt` / `thumbnail` | string | 页面图、替代页面图和缩略图 URL |
| `note` | string | 备注 |
| `shapes` | array | 图形对象列表 |
| `problem` | object / null | 关联题目，普通页面可为空 |

### 7.3 题目对象：problem

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `problemId` | string | 题目 ID |
| `problemType` | integer | 题型编号，见 A18 |
| `body` | string | 题干 |
| `score` | integer | 分值数值，展示换算为 score / 100 分 |
| `remark` | string | 解析/备注，是否返回以实际响应为准 |
| `answers` | array | 答案数据，是否提供及元素结构以实际响应为准 |
| `hasRemark` | boolean | 解析/备注标志 |
| `options` | array / null | 选项列表 |
| `pollingCount` | integer / null | 投票数量限制信息，具体规则待确认 |
| `dt` | integer / null | 题目时间，也可从开放事件或时间线取得 |
| `blanks` | array | 填空配置 |

### 7.4 选项对象：options[]

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `key` | string | 提交答案用的选项标识 |
| `value` | string | 选项显示文字 |

### 7.5 填空对象：blanks[]

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `num` | integer | 空位编号 |
| `score` | integer | 该空的分值数值 |
| `answers` | array of string | 答案配置，是否提供以实际响应为准 |
| `caseSensitive` | boolean | 是否区分大小写 |
| `fuzzyMatch` | boolean | 是否允许模糊匹配 |

### 7.6 图形对象：shapes[]

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `Text` | string / null | 文字 |
| `Fill` | object | 填充属性 |
| `Line` | object | 线条属性 |
| `Width` / `Height` | number | 宽度 / 高度 |
| `Left` / `Top` | number | 位置 |
| `Rotation` | number | 旋转数值，单位待确认 |
| `ZOrderPosition` | integer | 叠放顺序 |
| `PPTShapeId` | integer | 图形 ID |
| `PPTShapeType` | integer | 图形类型，枚举待确认 |

### 7.7 填充对象：Fill

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `BackColor` | string | 颜色，如 #FFFFFF |
| `Transparency` | number | 透明度，范围待确认 |
| `Visble` | boolean | 可见性，保留原始字段拼写 |

### 7.8 线条对象：Line

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `DashStyle` | string | 线型，如 msoLineSolid |
| `BackColor` | string | 线条颜色 |
| `Visble` | boolean | 可见性，保留原始字段拼写 |
| `Weight` | number | 线宽，单位待确认 |

## 8. 课堂 WebSocket

### 8.1 连接与认证

连接选定服务的 WebSocket 地址，见 1.1。所有消息为 JSON 对象，顶层 op 标识类型。连接建立后发送 hello，使用 lessonToken 认证；课堂 Bearer 不替代 hello.auth。服务端既返回初始状态，也主动推送事件，不假定每条请求都有同名响应。

### 8.2 进入课堂：客户端 → 服务端

```json
{
  "op": "hello",
  "userid": "<用户ID>",
  "role": "student",
  "auth": "<lessonToken>",
  "lessonid": "<本次课堂ID>"
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `op` | string | hello |
| `userid` | string | A04 的用户 ID |
| `role` | string | student |
| `auth` | string | A13 的 data.lessonToken |
| `lessonid` | string | 本次课堂 ID，注意全小写字段名 |

### 8.3 离开课堂：客户端 → 服务端

```json
{
  "op": "leavelesson",
  "lessonid": "<本次课堂ID>"
}
```

lessonid 指定离开的课堂。离课确认消息格式待确认，不定义固定 ACK 外壳。

### 8.4 服务端消息索引

| 编号 | op | 含义 |
| --- | --- | --- |
| W01 | `hello` | 返回课堂初始状态 |
| W02 | `unlockproblem` | 开放题目 |
| W03 | `showpresentation` | 展示或切换课件 |
| W04 | `slide` | 课堂翻页 |
| W05 | `slidenav` | 另一种翻页消息 |
| W06 | `extendtime` | 增加作答时间 |
| W07 | `callpaused` | 随机点名事件 |
| W08 | `showfinished` | 课件放映结束 |
| W09 | `lessonfinished` | 课堂结束 |
| W10 | `locationcheckinext` | 位置签到开始 |
| W11 | `locationcheckinend` | 位置签到结束 |

### W01. 返回课堂初始状态

**预期消息**

```json
{
  "op": "hello",
  "presentation": "<课件ID>",
  "slideindex": 1,
  "timeline": []
}
```

presentation 为课件 ID，用于 A17；slideindex 为课堂实时页码，按从 1 开始转换；timeline 为历史事件数组。时间线最近的 slide 事件可提供较新的 pres 和 si。

课堂已结束的已知消息：

```json
{
  "op": "hello",
  "message": "lesson finished"
}
```

message 为 lesson finished 时表示课堂已结束；通用认证失败结构待确认。

### W02. 开放题目

**预期消息**

```json
{
  "op": "unlockproblem",
  "problem": {
    "prob": "<题目ID>",
    "limit": 60,
    "dt": 1700000000000
  }
}
```

problem.prob 为题目 ID；problem.limit 为作答秒数；problem.dt 为题目事件的毫秒时间，可用于关联补交。题干和选项从 A17 获取。

### W03. 展示或切换课件

**预期消息**

```json
{
  "op": "showpresentation",
  "presentation": "<课件ID>",
  "slideindex": 2,
  "timeline": []
}
```

presentation 为目标课件 ID；slideindex 为实时页码；timeline 为附带历史事件。课件变化时可调用 A17 获取。

### W04. 课堂翻页

**预期消息**

```json
{
  "op": "slide",
  "slideindex": 3
}
```

slideindex 为实时页码，示例 3 对应从零计数的数组索引 2。

### W05. 另一种翻页消息

**预期消息**

```json
{
  "op": "slidenav",
  "slide": {
    "si": 3
  }
}
```

slide.si 为实时页码，转换同 slide；它不是顶层 si。

### W06. 增加作答时间

**预期消息**

```json
{
  "op": "extendtime",
  "problem": {
    "extend": 30
  }
}
```

problem.extend 为增加的秒数，示例在剩余时间上加 30 秒，不是将总时限设为 30 秒。其他题目关联字段待确认。

### W07. 随机点名事件

**预期消息**

```json
{
  "op": "callpaused",
  "event": {
    "code": "RANDOM_PICK",
    "title": "示例随机点名事件",
    "dt": 1700000000000
  }
}
```

event.code 为 RANDOM_PICK；event.title 为标题；event.dt 为事件毫秒时间。被点名人员名单结构待确认。

### W08. 课件放映结束

**预期消息**

```json
{
  "op": "showfinished",
  "event": {
    "code": "SHOW_FINISH",
    "title": "示例放映结束事件",
    "dt": 1700000000000
  }
}
```

event.code 为 SHOW_FINISH；title 和 dt 为标题及毫秒时间。课件放映结束与整堂课结束是两个事件。

### W09. 课堂结束

**预期消息**

```json
{
  "op": "lessonfinished",
  "event": {
    "code": "LESSON_FINISH",
    "title": "示例下课事件",
    "dt": 1700000000000
  }
}
```

event.code 为 LESSON_FINISH；title 和 dt 为标题及毫秒时间。此事件不等于客户端发送离课消息。

### W10. 位置签到开始

**预期消息**

以下为待确认的兼容形态，时间和坐标字段是否实际返回以响应为准。

```json
{
  "op": "locationcheckinext",
  "startMs": 1700000000000,
  "centerLat": 31.2304,
  "centerLng": 121.4737
}
```

startMs 为开始毫秒时间候选字段，serverTime 可作为兼容候选；centerLat/centerLng 为中心坐标候选，具体返回结构待确认。收到事件后可调用 A15，事件本身不表示账号已签到。

### W11. 位置签到结束

**预期消息**

```json
{
  "op": "locationcheckinend",
  "endedAt": 1700000060000
}
```

endedAt 为结束毫秒时间候选字段，其他兼容候选为 endMs、serverTime，具体返回组合待确认。结束事件不替代 A15 的签到状态。

### 8.5 时间线对象：timeline[]

```json
{
  "type": "problem",
  "title": "示例题目发布事件",
  "dt": 1700000000000,
  "si": 1,
  "limit": 60,
  "prob": "<题目ID>",
  "pres": "<课件ID>"
}
```

| 字段 | 类型 | 含义 / 传值规则 |
| --- | --- | --- |
| `type` | string | 事件类型，已知 event、problem、slide |
| `code` | string，若返回 | 事件码，如 RANDOM_PICK、SHOW_FINISH、LESSON_FINISH |
| `title` | string，若返回 | 事件标题 |
| `dt` | integer | 事件 Unix 毫秒时间，题目时间可用于 A19 |
| `si` | integer，若返回 | 关联页码/索引，各事件起点约定待确认 |
| `total` | integer，若返回 | 总量字段，完整语义待确认 |
| `limit` | integer，若返回 | 作答时限，单位秒 |
| `prob` | string，若返回 | 关联题目 ID |
| `pres` | string，若返回 | 关联课件 ID |

timeline[].si、slides[].index 和实时 slideindex 不能未经确认就按同一索引起点处理。定位历史题目时需核对课件 ID、题目 ID 和页面内容。

## 9. 已知状态码

| 取值 | 上下文 | 含义 / 后续处理 |
| --- | --- | --- |
| `code: 0` | 采用数字业务码的接口 | 操作成功，仍需取得对应业务数据 |
| `code: 50001` | A06 | 扫码等待超时，重新取得二维码 |
| `code: 50070` | A13 | 要求动态二维码，先执行 A14 |
| `code: 51203` | 动态扫码签到流程 | 二维码过期，取得新的有效二维码 |
| `wx_errcode=405` | A09 | 微信授权完成，取得 code 后执行 A10 |
| `success: true` | A20 / A21 兼容响应 | 获取上传凭证或上传成功，A21 返回形态待确认 |
| `message: "lesson finished"` | WebSocket hello | 课堂已结束 |

WebSocket 的 event.code 为字符串事件类型，不是 HTTP 数字业务码。未列出的业务码、错误外壳及重试规则待确认。

## 10. 接口调用关系

| 场景 | 调用顺序 |
| --- | --- |
| 密码登录 | 腾讯验证 → A03 → 保留会话 Cookie → A04 |
| 短信登录 | 腾讯验证 → A01 → A02 → A03（type: 3）→ A04 |
| 二维码 token 登录 | A05 → 展示二维码 → A06 → A04；超时后重新 A05 |
| 微信网页授权 | A07 → A08 → A09 → A10 → A04 |
| 查询课堂 | A11 与 A12，按课程 ID 关联 |
| 普通课堂免扫码入课 | A12 取得 lessonId → A13 |
| 动态二维码入课 | A14 取得 lessonId → A13 |
| 建立实时连接 | A13 取得 lessonToken → WebSocket hello |
| 获取课件 | WebSocket 取得课件 ID → A17，携带 A13 的 Bearer |
| 提交答案 | A17 取得题目 → 等待开放事件 → A18；补交用 A19 |
| 主观题图片 | A20 → A21 → 拼接资源 URL → A18/A19 |
| 位置签到 | 开始事件 → A15 → 按条件 A16 → A15 确认 |

签到或答题请求针对单一账号；多个账号需要分别携带各自认证信息发送请求。
