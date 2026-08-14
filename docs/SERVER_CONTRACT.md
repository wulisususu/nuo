# Server Contract v1

本文件是 Android 客户端与后续服务器智能体之间的稳定边界。服务器可以在内部映射到已有接口，但不要修改 Android UI 层来适配服务器。

## Base URL

用户配置 HTTPS Base URL。客户端拒绝明文 HTTP。

## Authentication

若配置 Token：`Authorization: Bearer <token>`。

## 查询

`POST /api/license/query`

```json
{"code":"42531563"}
```

## 统一动作

`POST /api/license/action`

激活：
```json
{"code":"42531563","action":"activate"}
```

退款停用：
```json
{"code":"42531563","action":"refund_disable"}
```

续期：
```json
{"code":"42531563","action":"renew","days":30}
```

解绑：
```json
{"code":"42531563","action":"unbind"}
```

推荐返回：
```json
{"success":true,"message":"操作成功","data":{"code":"42531563","status":"active","expiresAt":"2026-10-14T00:00:00+08:00"}}
```

客户端兼容 `success/ok`、`message/msg`、`status/state`、`expiresAt/expire_at/expires_at`。

## 业务语义

- `refund_disable`：因退款停用，服务端应保留该原因，区别于自然过期等状态。
- `renew`：`days` 为 1..3650；服务器接入时必须明确从当前到期时间还是当前时间续。
- `unbind`：解除设备/HWID 绑定，不删除激活码。

## 幂等

`activate`、`refund_disable`、`unbind` 建议幂等。`renew` 不是天然幂等，服务器应增加 requestId/幂等键。Android V1 不自动重试写操作。
