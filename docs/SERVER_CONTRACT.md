# activation-code-system Android Adapter

Android 客户端直接复用 `wulisususu/activation-code-system` 当前后台接口，不额外要求服务器实现新的 `/api/license/*` 包装层。

## Base URL

客户端接受：

```text
http://HOST
https://HOST
```

构建时可通过以下 Gradle property / 环境变量预配置：

```text
ACTIVATION_BASE_URL
ACTIVATION_BASIC_USER
ACTIVATION_BASIC_PASSWORD
```

公开仓库不得提交真实密码。

## Authentication

若 Basic Auth 用户名和密码都存在：

```http
Authorization: Basic base64(username:password)
```

否则若存在 Bearer Token：

```http
Authorization: Bearer <token>
```

Basic Auth 优先，禁止同时发送两个 `Authorization` 方案。

## Query

先查正式授权：

```http
GET /backend/cards?q=<code>&limit=20&offset=0
```

客户端必须从 `items` 中做完全匹配：

```text
card_secret == code || card_no == code
```

如果正式授权完全匹配为 0，再只读查询库存：

```http
GET /api/test-card-stock/list?q=<code>&page=1&page_size=20
```

只有正式授权和库存都完全不存在时，V3 才允许新建卡。

## V3 Create

创建正式卡：

```http
POST /backend/cards
Content-Type: application/json
```

共同字段：

```json
{
  "card_no": "12345678",
  "card_secret": "12345678",
  "status": "unused",
  "duration_kind": "PERMANENT",
  "source_type": "DIRECT",
  "binding_status": "unbound"
}
```

创建通用卡额外写入：

```json
{
  "game_scope": "ALL",
  "scope": "ALL",
  "legacy_compatible": true
}
```

创建绝区零专属卡：

```json
{
  "game_scope": "ZZZ",
  "scope": "ZZZ",
  "legacy_compatible": false
}
```

创建鸣潮专属卡：

```json
{
  "game_scope": "WUWA",
  "scope": "WUWA",
  "legacy_compatible": false
}
```

创建阶段禁止预激活。新卡保持 `unused + unbound`，第一次真实客户端验证成功后由服务器自动变成 `activated` 并绑定设备。

## Client verification and app_id

服务端只使用 `app_id -> scope` 映射决定请求属于哪个游戏，不信任客户端 `game` 字段。

当前 V3 关注映射：

```text
zzz-remielle -> ZZZ
zzz -> ZZZ
wuwa-zigrika-commercial -> WUWA
wuwa -> WUWA
```

主验证接口：

```http
POST /api/verify
```

新版 verify 阶段至少需要：

```json
{
  "stage": "verify",
  "hwid": "MACHINE-HASH",
  "card": "12345678",
  "password": "12345678",
  "app_id": "zzz-remielle",
  "request_id": "unique-request-id"
}
```

`request_id` 长度 8~128 字符；同一次重试必须复用同一个 request_id 和完全一致的请求参数。

服务器会校验：

- `app_id` 是否存在服务端映射；
- 设备是否正常、是否被封禁或退款锁定；
- 卡号与卡密是否匹配；
- 卡范围是 `ALL` 或与当前 app_id 映射 scope 一致；
- 卡是否停用、删除、过期；
- 卡是否已经绑定另一台设备；
- 当前设备同 scope 槽位是否已被其他不可替换授权占用；
- 如果来自库存，还会校验库存状态、库存分配模式和 scope。

## Actions

激活：

```http
POST /backend/cards/{card_id}/activate
Content-Type: application/json

{}
```

退款停用：

```http
POST /backend/cards/{card_id}/refund/disable
Content-Type: application/json

{}
```

续期（服务端仍支持，V3 悬浮窗不显示该按钮）：

```http
POST /backend/cards/{card_id}/renew
Content-Type: application/json

{"hours":720}
```

规则：`1..998` 为有限小时，`999` 为永久。

解绑：

```http
POST /backend/cards/{card_id}/unbind
```

## Error handling

- HTTP 401：显示 Basic Auth 认证失败或服务器原始错误信息。
- HTTP 4xx/5xx：优先解析 `message` / `msg` / `error` / `detail`。
- 不自动重试写动作。
- 模糊搜索出现多个完全相同记录时停止操作，要求后台检查数据。
