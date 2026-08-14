package com.wulisu.licenseoverlay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.wulisu.licenseoverlay.config.BasicPasswordStore
import com.wulisu.licenseoverlay.config.ConfigStore
import com.wulisu.licenseoverlay.config.TokenStore
import com.wulisu.licenseoverlay.service.OverlayService

class MainActivity : Activity() {
    private lateinit var config: ConfigStore
    private lateinit var tokenStore: TokenStore
    private lateinit var basicPasswordStore: BasicPasswordStore
    private lateinit var status: TextView
    private lateinit var baseUrl: EditText
    private lateinit var basicUsername: EditText
    private lateinit var basicPassword: EditText
    private lateinit var token: EditText
    private var startAfterPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigStore(this)
        tokenStore = TokenStore(this)
        basicPasswordStore = BasicPasswordStore(this)
        setContentView(buildContent())
    }

    override fun onResume() {
        super.onResume()
        if (startAfterPermission && Settings.canDrawOverlays(this)) {
            startAfterPermission = false
            startOverlayService()
        }
        renderStatus()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }

        root.addView(text("激活助手·悬浮窗版 V2", 24f, true))
        root.addView(text("无需无障碍。亮屏时悬浮球常驻所有页面；复制数字后点“码”即可查询。未创建的 6–12 位纯数字可以直接创建为测试服通用卡。", 14f, false).apply {
            setPadding(0, dp(8), 0, dp(16))
        })

        status = text("", 14f, false)
        root.addView(status)

        root.addView(button("启用悬浮球") { enableOverlay() })
        root.addView(button("关闭悬浮球") {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "悬浮球已关闭", Toast.LENGTH_SHORT).show()
            window.decorView.postDelayed({ renderStatus() }, 250)
        })
        root.addView(button("打开悬浮窗权限设置") { openOverlayPermission() })

        root.addView(section("服务器"))
        baseUrl = input("HTTP / HTTPS 服务器地址", config.baseUrl)
        root.addView(baseUrl)

        basicUsername = input("Basic Auth 用户名（可空）", config.basicUsername)
        root.addView(basicUsername)

        basicPassword = input("Basic Auth 密码（可空）", basicPasswordStore.read()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(basicPassword)

        token = input("Bearer Token（Basic Auth 未配置时才使用）", tokenStore.read()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(token)

        root.addView(button("保存配置") {
            config.baseUrl = baseUrl.text.toString()
            config.basicUsername = basicUsername.text.toString()
            basicPasswordStore.save(basicPassword.text.toString())
            tokenStore.save(token.text.toString())
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            renderStatus()
        })

        root.addView(section("V2 创建规则"))
        root.addView(text(
            "当剪贴板识别到 6–12 位纯数字，且服务器中不存在该卡时，“创建”按钮会启用。创建后直接成为测试服通用正式卡：ALL 通用范围、永久有效、已激活、未绑定。已经存在的卡不会重复创建。",
            13f,
            false
        ))

        root.addView(section("与 V4 共存"))
        root.addView(text("本版包名为 com.wulisu.licenseoverlay.overlayonly，不会覆盖原 V4。测试本版时，建议先在原 V4 里关闭无障碍服务，避免出现两个“码”悬浮球。", 13f, false))

        return ScrollView(this).apply { addView(root) }
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startAfterPermission = true
            openOverlayPermission()
            return
        }
        startOverlayService()
    }

    private fun openOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun startOverlayService() {
        startForegroundService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "悬浮球已启用", Toast.LENGTH_SHORT).show()
        window.decorView.postDelayed({ renderStatus() }, 250)
    }

    private fun renderStatus() {
        val permission = Settings.canDrawOverlays(this)
        val running = OverlayService.INSTANCE != null
        status.text = "悬浮窗权限：${if (permission) "已允许" else "未允许"}\n悬浮服务：${if (running) "运行中" else "未运行"}\n服务器：${config.baseUrl.ifBlank { "未配置" }}"
        status.setTextColor(if (permission && running) 0xFF1B7F3A.toInt() else 0xFFB3261E.toInt())
    }

    private fun section(value: String) = text(value, 17f, true).apply { setPadding(0, dp(22), 0, dp(8)) }

    private fun input(hint: String, value: String) = EditText(this).apply {
        this.hint = hint
        setText(value)
        setSingleLine(true)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        gravity = Gravity.CENTER
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.rgb(32, 33, 36))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
