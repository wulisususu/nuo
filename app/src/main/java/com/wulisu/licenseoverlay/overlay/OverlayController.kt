package com.wulisu.licenseoverlay.overlay

import android.accessibilityservice.AccessibilityService
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
import com.wulisu.licenseoverlay.clipboard.ClipboardBridge
import com.wulisu.licenseoverlay.config.ConfigStore
import com.wulisu.licenseoverlay.config.TokenStore
import com.wulisu.licenseoverlay.core.*

class OverlayController(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val config = ConfigStore(service)
    private val tokenStore = TokenStore(service)
    private val api = LicenseApi({ config.baseUrl }, { tokenStore.read() })
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var targetActive = false
    private var currentCode: String? = null
    private var currentActionable = false
    private var latestRequestId = 0L
    private var confirmAction: LicenseAction? = null
    private var confirmUntil = 0L

    fun setTargetActive(active: Boolean) { targetActive = active; if (active) showBubble() else hideAll() }
    fun destroy() { hideAll(); api.close() }

    private fun showBubble() {
        if (!targetActive || bubble != null || panel != null) return
        val view = TextView(service).apply {
            text = "码"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(0xB9222222.toInt(), dp(22).toFloat())
            setOnClickListener { readClipboard() }
        }
        val params = WindowManager.LayoutParams(dp(42), dp(42), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = dp(6); y = dp(280) }
        installDrag(view, params)
        wm.addView(view, params)
        bubble = view
    }

    private fun installDrag(view: View, params: WindowManager.LayoutParams) {
        var downX = 0f; var downY = 0f; var startY = 0; var moved = false
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startY = params.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.rawY - downY) > dp(4) || kotlin.math.abs(event.rawX - downX) > dp(4)) moved = true
                    params.y = (startY + (event.rawY - downY).toInt()).coerceAtLeast(0)
                    runCatching { wm.updateViewLayout(view, params) }; true
                }
                MotionEvent.ACTION_UP -> { if (!moved) view.performClick(); true }
                else -> false
            }
        }
    }

    private fun readClipboard() {
        ClipboardBridge.request(service) { text -> handler.post {
            if (!targetActive) return@post
            currentActionable = false
            when (val result = ActivationCodeParser.parse(text.orEmpty())) {
                is ParseResult.Found -> { currentCode = result.code; openPanel("已识别，正在查询…"); queryCurrent() }
                is ParseResult.Ambiguous -> { currentCode = null; openPanel("检测到多个疑似激活码：${result.codes.joinToString(" / ")}") }
                ParseResult.NotFound -> { currentCode = null; openPanel("剪贴板中未找到激活码") }
            }
        } }
    }

    private fun openPanel(initialMessage: String) {
        removeBubble(); removePanel()
        val root = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); background = rounded(0xE61E1E1E.toInt(), dp(16).toFloat()) }
        val titleRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        val title = label("激活助手", 16f, true).apply { layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f); gravity = Gravity.CENTER_VERTICAL }
        val close = Button(service).apply { text = "×"; textSize = 18f; minWidth = 0; minimumWidth = 0; setPadding(0,0,0,0); setOnClickListener { collapse() } }
        titleRow.addView(title); titleRow.addView(close, LinearLayout.LayoutParams(dp(44), dp(36))); root.addView(titleRow)
        root.addView(label(currentCode ?: "—", 20f, true), LinearLayout.LayoutParams(-1, dp(42)))
        root.addView(label(initialMessage, 13f, false).apply { tag = TAG_STATUS }, LinearLayout.LayoutParams(-1, dp(64)))
        val row1 = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(actionButton("激活", LicenseAction.ACTIVATE), weightedButtonParams()); row1.addView(actionButton("退款停用", LicenseAction.REFUND_DISABLE), weightedButtonParams()); root.addView(row1)
        val row2 = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(actionButton(renewButtonLabel(), LicenseAction.RENEW), weightedButtonParams()); row2.addView(actionButton("解绑", LicenseAction.UNBIND), weightedButtonParams()); root.addView(row2)
        root.addView(Button(service).apply { text = "重新读取剪贴板"; isAllCaps = false; setOnClickListener { readClipboard() } }, LinearLayout.LayoutParams(-1, dp(42)))
        val params = WindowManager.LayoutParams(dp(285), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER_VERTICAL or Gravity.END; x = dp(8) }
        wm.addView(root, params); panel = root; setButtonsEnabled(false)
    }

    private fun renewButtonLabel(): String {
        val hours = config.renewHours
        return when {
            hours == 999 -> "续期 永久"
            hours % 24 == 0 -> "续期 ${hours / 24} 天"
            else -> "续期 $hours 小时"
        }
    }

    private fun actionButton(text: String, action: LicenseAction): Button = Button(service).apply { this.text = text; isAllCaps = false; tag = action; setOnClickListener { onAction(action, this) } }

    private fun onAction(action: LicenseAction, button: Button) {
        val code = currentCode ?: return
        if (!currentActionable) return
        val now = System.currentTimeMillis()
        if (action.destructive && (confirmAction != action || now > confirmUntil)) {
            confirmAction = action; confirmUntil = now + 3_000
            val original = button.text; button.text = if (action == LicenseAction.UNBIND) "确认解绑" else "确认退款停用"
            handler.postDelayed({ if (button.isAttachedToWindow && confirmAction == action && System.currentTimeMillis() >= confirmUntil) { button.text = original; confirmAction = null } }, 3_050)
            return
        }
        confirmAction = null
        val command = if (action == LicenseAction.RENEW) LicenseCommand(code, action, config.renewHours) else LicenseCommand(code, action)
        setButtonsEnabled(false); setStatus("处理中…")
        latestRequestId = api.execute(command) { requestId, result -> handler.post {
            if (requestId != latestRequestId || currentCode != code) return@post
            if (result is ApiResult.Success) currentActionable = result.value.source == "backend"
            renderResult(result); setButtonsEnabled(currentActionable)
        } }
    }

    private fun queryCurrent() {
        val code = currentCode ?: return
        setButtonsEnabled(false)
        latestRequestId = api.query(code) { requestId, result -> handler.post {
            if (requestId != latestRequestId || currentCode != code) return@post
            currentActionable = result is ApiResult.Success && result.value.source == "backend"
            renderResult(result)
            setButtonsEnabled(currentActionable)
        } }
    }

    private fun renderResult(result: ApiResult<LicenseSnapshot>) {
        when (result) {
            is ApiResult.Success -> {
                val s = result.value
                val parts = mutableListOf<String>()
                parts += if (s.source == "stock") "类型：库存码" else "类型：正式授权"
                parts += "状态：${s.status}"
                s.bindingStatus?.let { parts += "绑定：$it" }
                s.expiresAt?.let { parts += "到期：$it" }
                if (s.message.isNotBlank()) parts += s.message
                setStatus(parts.joinToString("\n"))
            }
            is ApiResult.Failure -> setStatus("失败：${result.message}")
        }
    }

    private fun setStatus(text: String) { panel?.findViewWithTag<TextView>(TAG_STATUS)?.text = text }
    private fun setButtonsEnabled(enabled: Boolean) { LicenseAction.entries.forEach { panel?.findViewWithTag<Button>(it)?.isEnabled = enabled } }
    private fun collapse() { removePanel(); if (targetActive) showBubble() }
    private fun hideAll() { removePanel(); removeBubble(); currentCode = null; currentActionable = false; latestRequestId = -1L }
    private fun removeBubble() { bubble?.let { runCatching { wm.removeView(it) } }; bubble = null }
    private fun removePanel() { panel?.let { runCatching { wm.removeView(it) } }; panel = null }
    private fun label(text: String, size: Float, bold: Boolean) = TextView(service).apply { this.text = text; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL; if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD) }
    private fun weightedButtonParams() = LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun dp(value: Int): Int = (value * service.resources.displayMetrics.density + 0.5f).toInt()
    companion object { private const val TAG_STATUS = "status" }
}
