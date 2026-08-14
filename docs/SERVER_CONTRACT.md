# Server Contract v2 — activation-code-system adapter

本客户端已经直接适配 `wulisususu/activation-code-system` 当前 `main` 的后台卡密 API，不再要求服务器实现 `/api/license/query` 或 `/api/license/action`。

## Base URL

用户配置 HTTPS Base URL。客户端拒绝明文 HTTP。

若配置 Token，会附带：

```http
Authorization: Bearer <token>
```

当前 `activation-code-system` 后台卡密路由本身未强制读取该 Header；可由 Nginx / API Gateway 在外层校验。

## 1. 按激活码解析正式授权

```http
GET /backend/cards?q=<urlencoded-code>&limit=20&offset=0
```

客户端只接受 `card_secret == code` 或 `card_no == code` 的**完全匹配**，不会直接使用模糊搜索结果。

若正式授权未命中，再只读查询库存：

```http
GET /api/test-card-stock/list?q=<urlencoded-code>&page=1&page_size=20
```

库存码只展示状态，不允许执行正式授权动作。

## 2. 四个动作

先由查询得到 `card_id`，再复用现有后台接口。

### 激活

```http
POST /backend/cards/{card_id}/activate
Content-Type: application/json

{}
```

### 退款停用

```http
POST /backend/cards/{card_id}/refund/disable
Content-Type: application/json

{}
```

该动作会使用现有 `disable_card_for_refund` 业务规则，停用卡并锁定当前绑定设备。

### 续期

```http
POST /backend/cards/{card_id}/renew
Content-Type: application/json

{"hours":720}
```

现有服务端规则：

- 只允许“已过期且仍持有当前绑定”的卡续期；
- `1..998` 表示有限小时数；
- `999` 表示转为永久授权；
- 服务端实现对 `hours >= 999` 都按永久处理，因此客户端强制限制为 `1..999`，防止误传更大的有限时长。

### 解绑

```http
POST /backend/cards/{card_id}/unbind
```

解绑只结束当前机器绑定，不删除卡。

## 3. 返回格式

`/backend/cards` 和动作接口都使用 `BackendCardOut`。客户端读取：

```text
id
card_no
card_secret
status
expires_at
binding_status
machine_code
```

库存查询读取：

```text
card_no
card_secret
status
```

## 4. 安全与并发

- 客户端只把解析后的纯数字激活码放进查询 URL，不上传整段剪贴板。
- 写操作前会再次按激活码解析当前 `card_id`，不缓存长期 ID 映射。
- 退款停用与解绑在悬浮 UI 保留二次确认。
- 客户端不自动重试写操作，尤其是续期。
- `latestRequestId` 防止旧查询覆盖用户刚复制的新激活码。
