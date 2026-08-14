# AGENTS.md

## Project intent

这是一个自用的超轻量 Android 悬浮激活助手。日常流程固定为：闲鱼/千牛发货 -> 用户复制整段话术 -> 点击悬浮球 -> 提取纯数字激活码 -> 调服务器执行管理动作。

## Hard scope

V1 只做：

1. 闲鱼 / 千牛 / 已学习分身的前台门禁。
2. Accessibility Overlay 悬浮球与面板。
3. 用户主动点击后一次性读取剪贴板。
4. 激活码纯数字解析。
5. 查询 + 四个动作：`activate`、`refund_disable`、`renew`、`unbind`。
6. HTTPS 服务器适配。

不要顺手加入：OCR、截图、录屏、聊天页面节点读取、自动发消息、自动点击闲鱼/千牛、WebView、Compose、Room、Retrofit、OkHttp、统计 SDK、广告 SDK。

## Privacy / permission invariant

`accessibility_service_config.xml` 必须保持 `android:canRetrieveWindowContent="false"`。AccessibilityService 只用事件的 `packageName` 做前台 App 门禁。

不要后台监听剪贴板。Android 10+ 必须保持“用户点击悬浮球 -> ClipboardBridgeActivity 获得焦点 -> 读取一次 -> finish”的模型。

剪贴板全文只用于内存解析，不落盘、不写日志、不上传。服务器只接收最终激活码及动作参数。

## Server boundary

以 `docs/SERVER_CONTRACT.md` 为准。Android UI 不应知道服务器内部业务实现。所有服务器差异集中到 `api/LicenseApi.kt` 或未来 adapter 层。

四个动作 wire name 是稳定 API：

```text
activate
refund_disable
renew
unbind
```

不要把 `refund_disable` 重命名成普通 `disable`；`renew` 必须带 `days`，范围 1..3650。

## Lightweight rules

优先 Android Framework + Kotlin stdlib。新增任何第三方依赖前先证明 Framework 无法合理实现。

Release 必须保持 `minifyEnabled = true` 和 `shrinkResources = true`。不要引入 AndroidX，除非出现明确且无法绕开的 API 需要。

## Target app / clone behavior

默认包：`com.taobao.idlefish`、`com.taobao.qianniu`。OEM 分身不可硬猜后缀，使用 `TargetAppRegistry` 学习实际 packageName。

若 OEM 使用独立 Profile 且剪贴板隔离，不要尝试跨 Profile 绕过；在对应 Profile 安装/运行助手并做实机验证。

## Concurrency invariants

- 写请求处理中禁用动作按钮。
- `latestRequestId` 防止旧响应覆盖新激活码。
- 多个候选激活码时禁止自动执行动作。
- `refund_disable`、`unbind` 必须保留二次确认。
- Android 客户端不要自动重试写动作，尤其是 `renew`。

## Validation before commit

至少执行 `scripts/CoreSmokeTest.kt` 的纯 Kotlin 烟雾测试。有 Android SDK/Gradle 时执行 `gradle :app:assembleDebug`。

## Server-agent handoff

服务器智能体接手时：

1. 扫描现有激活码表、鉴权和业务接口。
2. 实现/映射 `SERVER_CONTRACT.md`，不要先改 Android UI。
3. 明确 `refund_disable` 对数据库状态的语义。
4. 明确 `renew` 是从当前到期时间续还是从当前时间续。
5. 给 `renew` 加服务端幂等保护。
6. 返回统一的 `success/message/data` 结构。
7. 在真实服务器配置 HTTPS 后，再做 Android 实机联调。
