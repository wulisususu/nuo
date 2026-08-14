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

库存码只展示状态，不执行正式卡写动作。

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

续期：

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
