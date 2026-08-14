package com.wulisu.licenseoverlay

import android.app.Activity
import android.content.Intent
import android.graphics.Color
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
import com.wulisu.licenseoverlay.service.LicenseAccessibilityService

class MainActivity : Activity() {
    private lateinit var config: ConfigStore
    private lateinit var tokenStore: TokenStore
    private lateinit var basicPasswordStore: BasicPasswordStore
    private lateinit var status: TextView
    private lateinit var baseUrl: EditText
    private lateinit var basicUsername: EditText
    private lateinit var basicPassword: EditText
    private lateinit var token: EditText
    private lateinit var renewHours: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigStore(this)
        tokenStore = TokenStore(this)
        basicPasswordStore = BasicPasswordStore(this)
        setContentView(buildContent())
    }

    override fun onStart() {
        super.onStart()
        renderStatus()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }

        root.addView(text("激活助手", 24f, true))
        root.addView(text(
            "启用无障碍服务后，只要设备处于亮屏可交互状态，悬浮球会在所有页面显示。点击悬浮球只读取一次剪贴板，不再跳回本应用。",
            14f,
            false
        ).apply { setPadding(0, dp(8), 0, dp(16)) })

        status = text("", 14f, false)
        root.addView(status)

        root.addView(button("打开无障碍设置") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

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

        renewHours = input("默认续期小时数（1-998；999=永久）", config.renewHours.toString()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(renewHours)

        root.addView(button("保存配置") {
            val hours = renewHours.text.toString().toIntOrNull()?.coerceIn(1, 999) ?: 720
            config.baseUrl = baseUrl.text.toString()
            config.basicUsername = basicUsername.text.toString()
            basicPasswordStore.save(basicPassword.text.toString())
            config.renewHours = hours
            tokenStore.save(token.text.toString())
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            renderStatus()
        })

        root.addView(section("当前模式"))
        root.addView(text(
            "• 亮屏：悬浮球始终显示\n" +
                "• 灭屏：悬浮球自动隐藏\n" +
                "• 不区分闲鱼 / 千牛 / 微信 / 桌面 / 设置\n" +
                "• 点悬浮球：当前页面不切换，只读取剪贴板并识别激活码\n" +
                "• 识别成功后：直接在悬浮面板查询 / 激活 / 停用 / 续期 / 解绑",
            13f,
            false
        ))

        return ScrollView(this).apply { addView(root) }
    }

    private fun renderStatus() {
        val serviceEnabled = LicenseAccessibilityService.INSTANCE != null
        val auth = if (config.basicUsername.isNotBlank() && basicPasswordStore.read().isNotBlank()) {
            "Basic Auth 已配置"
        } else {
            "Basic Auth 未配置"
        }
        status.text = (if (serviceEnabled) "后台悬浮服务：已启用" else "后台悬浮服务：未启用") +
            "\n服务器：${config.baseUrl.ifBlank { "未配置" }}\n$auth"
        status.setTextColor(if (serviceEnabled) 0xFF1B7F3A.toInt() else 0xFFB3261E.toInt())
    }

    private fun section(value: String) = text(value, 17f, true).apply {
        setPadding(0, dp(22), 0, dp(8))
    }

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
