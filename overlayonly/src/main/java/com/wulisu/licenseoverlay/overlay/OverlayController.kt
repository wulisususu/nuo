package com.wulisu.licenseoverlay.overlay

import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.wulisu.licenseoverlay.api.ApiResult
import com.wulisu.licenseoverlay.api.LicenseApi
import com.wulisu.licenseoverlay.api.LicenseSnapshot
import com.wulisu.licenseoverlay.config.BasicPasswordStore
import com.wulisu.licenseoverlay.config.ConfigStore
import com.wulisu.licenseoverlay.config.TokenStore
import com.wulisu.licenseoverlay.core.ActivationCodeParser
import com.wulisu.licenseoverlay.core.LicenseAction
import com.wulisu.licenseoverlay.core.LicenseCommand
import com.wulisu.licenseoverlay.core.ParseResult

class OverlayController(private val service: Service) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val config = ConfigStore(service)
    private val tokenStore = TokenStore(service)
    private val basicPasswordStore = BasicPasswordStore(service)
    private val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val api = LicenseApi(
        { config.baseUrl },
        { tokenStore.read() },
        { config.basicUsername },
        { basicPasswordStore.read() }
    )

    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var screenActive = false
    private var clipboardReading = false
    private var currentCode: String? = null
    private var backendActionable = false
    private var createAllowed = false
    private var latestRequestId = 0L
    private var confirmAction: LicenseAction? = null
    private var confirmUntil = 0L

    fun setScreenActive(active: Boolean) {
        screenActive = active
        if (active) showBubble() else hideAll()
    }

    fun destroy() {
        hideAll()
        api.close()
    }

    private fun showBubble() {
        if (!screenActive || bubble != null || panel != null) return
        val view = TextView(service).apply {
            text = "码"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
            background = rounded(0xC8222222.toInt(), dp(22).toFloat())
            setOnClickListener { readClipboard() }
        }
        val params = WindowManager.LayoutParams(
            dp(42), dp(42), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(6)
            y = dp(280)
        }
        installDrag(view, params)
        wm.addView(view, params)
        bubble = view
        bubbleParams = params
    }

    private fun installDrag(view: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startY = params.y; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.rawY - downY) > dp(4) || kotlin.math.abs(event.rawX - downX) > dp(4)) moved = true
                    params.y = (startY + (event.rawY - downY).toInt()).coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(view, params) }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!moved) view.performClick(); true }
                else -> false
            }
        }
    }

    private fun readClipboard() {
        if (!screenActive || clipboardReading) return
        clipboardReading = true

        clipboardText()?.let {
            clipboardReading = false
            handleClipboard(it)
            return
        }

        val focusView: View
        val params: WindowManager.LayoutParams
        when {
            panel != null && panelParams != null -> { focusView = panel!!; params = panelParams!! }
            bubble != null && bubbleParams != null -> { focusView = bubble!!; params = bubbleParams!! }
            else -> { clipboardReading = false; return }
        }

        val originalFlags = params.flags
        params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        focusView.isFocusable = true
        focusView.isFocusableInTouchMode = true
        runCatching { wm.updateViewLayout(focusView, params) }
        focusView.requestFocus()

        handler.postDelayed({
            val text = clipboardText()
            focusView.clearFocus()
            params.flags = originalFlags
            runCatching { if (focusView.isAttachedToWindow) wm.updateViewLayout(focusView, params) }
            clipboardReading = false
            handleClipboard(text)
        }, 160)
    }

    private fun clipboardText(): String? = runCatching {
        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(service)?.toString()
    }.getOrNull()

    private fun handleClipboard(text: String?) {
        if (!screenActive) return
        if (text.isNullOrBlank()) {
            currentCode = null
            backendActionable = false
            createAllowed = false
            openPanel("未读取到剪贴板。请先复制内容后再点“码”。")
            return
        }

        backendActionable = false
        createAllowed = false
        when (val result = ActivationCodeParser.parse(text)) {
            is ParseResult.Found -> {
                currentCode = result.code
                openPanel("已识别，正在查询…")
                queryCurrent()
            }
            is ParseResult.Ambiguous -> {
                currentCode = null
                openPanel("检测到多个疑似激活码：${result.codes.joinToString(" / ")}")
            }
            ParseResult.NotFound -> {
                currentCode = null
                openPanel("剪贴板中未找到激活码")
            }
        }
    }

    private fun openPanel(initialMessage: String) {
        removeBubble(); removePanel()
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(0xEE1E1E1E.toInt(), dp(16).toFloat())
        }
        val titleRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        titleRow.addView(label("激活助手·悬浮窗 V2", 16f, true), LinearLayout.LayoutParams(0, dp(36), 1f))
        titleRow.addView(Button(service).apply {
            text = "×"; textSize = 18f; minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0)
            setOnClickListener { collapse() }
        }, LinearLayout.LayoutParams(dp(44), dp(36)))
        root.addView(titleRow)
        root.addView(label(currentCode ?: "—", 20f, true), LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(label(initialMessage, 13f, false).apply { tag = TAG_STATUS }, LinearLayout.LayoutParams(-1, dp(88)))

        val row1 = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(actionButton("激活", LicenseAction.ACTIVATE), weightedButtonParams())
        row1.addView(actionButton("退款停用", LicenseAction.REFUND_DISABLE), weightedButtonParams())
        root.addView(row1)

        val row2 = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(createButton(), weightedButtonParams())
        row2.addView(actionButton("解绑", LicenseAction.UNBIND), weightedButtonParams())
        root.addView(row2)

        root.addView(Button(service).apply {
            text = "重新读取剪贴板"; isAllCaps = false; setOnClickListener { readClipboard() }
        }, LinearLayout.LayoutParams(-1, dp(42)))

        val params = WindowManager.LayoutParams(
            dp(292), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; x = dp(8) }
        wm.addView(root, params)
        panel = root
        panelParams = params
        setActionAvailability(false, false)
    }

    private fun actionButton(text: String, action: LicenseAction) = Button(service).apply {
        this.text = text; isAllCaps = false; tag = action; setOnClickListener { onAction(action, this) }
    }

    private fun createButton() = Button(service).apply {
        text = "创建"
        isAllCaps = false
        tag = TAG_CREATE
        setOnClickListener { createCurrentCode() }
    }

    private fun onAction(action: LicenseAction, button: Button) {
        val code = currentCode ?: return
        if (!backendActionable) return
        val now = System.currentTimeMillis()
        if (action.destructive && (confirmAction != action || now > confirmUntil)) {
            confirmAction = action
            confirmUntil = now + 3_000
            val original = button.text
            button.text = if (action == LicenseAction.UNBIND) "确认解绑" else "确认退款停用"
            handler.postDelayed({
                if (button.isAttachedToWindow && confirmAction == action && System.currentTimeMillis() >= confirmUntil) {
                    button.text = original; confirmAction = null
                }
            }, 3_050)
            return
        }

        confirmAction = null
        val command = if (action == LicenseAction.RENEW) LicenseCommand(code, action, config.renewHours) else LicenseCommand(code, action)
        setActionAvailability(false, false)
        setStatus("处理中…")
        latestRequestId = api.execute(command) { requestId, result -> handler.post {
            if (requestId != latestRequestId || currentCode != code) return@post
            backendActionable = result is ApiResult.Success && result.value.source == "backend"
            createAllowed = false
            renderResult(result)
            setActionAvailability(backendActionable, createAllowed)
        } }
    }

    private fun createCurrentCode() {
        val code = currentCode ?: return
        if (!createAllowed) return
        if (!code.matches(Regex("\\d{9}"))) {
            setStatus("创建失败：测试服通用卡必须是 9 位纯数字")
            return
        }

        backendActionable = false
        createAllowed = false
        setActionAvailability(false, false)
        setStatus("正在创建测试服通用卡…")
        latestRequestId = api.createGeneralStock(code) { requestId, result -> handler.post {
            if (requestId != latestRequestId || currentCode != code) return@post
            renderResult(result)
            backendActionable = false
            createAllowed = false
            setActionAvailability(false, false)
        } }
    }

    private fun queryCurrent() {
        val code = currentCode ?: return
        setActionAvailability(false, false)
        latestRequestId = api.query(code) { requestId, result -> handler.post {
            if (requestId != latestRequestId || currentCode != code) return@post

            when (result) {
                is ApiResult.Success -> {
                    backendActionable = result.value.source == "backend"
                    createAllowed = false
                    renderResult(result)
                }
                is ApiResult.Failure -> {
                    backendActionable = false
                    if (result.httpCode == 404) {
                        createAllowed = code.matches(Regex("\\d{9}"))
                        setStatus(
                            if (createAllowed) {
                                "状态：未创建\n绑定：未绑定\n可点击“创建”，将建立测试服通用永久卡"
                            } else {
                                "状态：未创建\n绑定：未绑定\n创建测试服通用卡要求 9 位纯数字"
                            }
                        )
                    } else {
                        createAllowed = false
                        renderResult(result)
                    }
                }
            }
            setActionAvailability(backendActionable, createAllowed)
        } }
    }

    private fun renderResult(result: ApiResult<LicenseSnapshot>) {
        when (result) {
            is ApiResult.Success -> {
                val s = result.value
                val parts = mutableListOf(
                    if (s.source == "stock") "类型：测试服库存卡" else "类型：正式授权",
                    "状态：${statusToChinese(s.status)}"
                )
                s.bindingStatus?.let { parts += "绑定：${bindingToChinese(it)}" }
                s.expiresAt?.let { parts += "到期：$it" }
                if (s.message.isNotBlank()) parts += s.message
                setStatus(parts.joinToString("\n"))
            }
            is ApiResult.Failure -> setStatus("失败：${result.message}")
        }
    }

    private fun statusToChinese(value: String): String = when (value.lowercase()) {
        "unused" -> "未使用"
        "available" -> "可用"
        "issued" -> "已发出"
        "activated" -> "已激活"
        "used" -> "已使用"
        "expired" -> "已过期"
        "disabled" -> "已停用"
        "deleted" -> "已删除"
        "discarded" -> "已废弃"
        "processing" -> "处理中"
        "unknown" -> "未知"
        else -> value
    }

    private fun bindingToChinese(value: String): String = when (value.lowercase()) {
        "unbound" -> "未绑定"
        "bound" -> "已绑定"
        "binding" -> "绑定中"
        "refund_blocked" -> "退款锁定"
        "blocked" -> "已锁定"
        "released" -> "已解绑"
        "unknown" -> "未知"
        else -> value
    }

    private fun setStatus(value: String) { panel?.findViewWithTag<TextView>(TAG_STATUS)?.text = value }

    private fun setActionAvailability(backendEnabled: Boolean, createEnabled: Boolean) {
        panel?.findViewWithTag<Button>(LicenseAction.ACTIVATE)?.isEnabled = backendEnabled
        panel?.findViewWithTag<Button>(LicenseAction.REFUND_DISABLE)?.isEnabled = backendEnabled
        panel?.findViewWithTag<Button>(LicenseAction.UNBIND)?.isEnabled = backendEnabled
        panel?.findViewWithTag<Button>(TAG_CREATE)?.isEnabled = createEnabled
    }

    private fun collapse() { removePanel(); if (screenActive) showBubble() }

    private fun hideAll() {
        removePanel(); removeBubble(); currentCode = null; backendActionable = false; createAllowed = false; latestRequestId = -1L; clipboardReading = false
    }

    private fun removeBubble() { bubble?.let { runCatching { wm.removeView(it) } }; bubble = null; bubbleParams = null }
    private fun removePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel = null; panelParams = null }

    private fun label(value: String, size: Float, bold: Boolean) = TextView(service).apply {
        text = value; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun weightedButtonParams() = LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val TAG_STATUS = "status"
        private const val TAG_CREATE = "create"
    }
}
