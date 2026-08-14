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
import com.wulisu.licenseoverlay.config.ConfigStore
import com.wulisu.licenseoverlay.config.TargetAppRegistry
import com.wulisu.licenseoverlay.config.TokenStore
import com.wulisu.licenseoverlay.service.LicenseAccessibilityService
import com.wulisu.licenseoverlay.service.MainActivityEvents

class MainActivity : Activity() {
    private lateinit var config: ConfigStore
    private lateinit var tokenStore: TokenStore
    private lateinit var registry: TargetAppRegistry
    private lateinit var status: TextView
    private lateinit var packageList: TextView
    private lateinit var baseUrl: EditText
    private lateinit var token: EditText
    private lateinit var renewHours: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = ConfigStore(this)
        tokenStore = TokenStore(this)
        registry = TargetAppRegistry(this)
        setContentView(buildContent())
    }

    override fun onStart() {
        super.onStart()
        MainActivityEvents.listener = { label, pkg ->
            runOnUiThread {
                Toast.makeText(this, "$label 已学习：$pkg", Toast.LENGTH_LONG).show()
                renderStatus()
            }
        }
        renderStatus()
    }

    override fun onStop() {
        MainActivityEvents.listener = null
        super.onStop()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }

        root.addView(text("激活助手", 24f, true))
        root.addView(text("仅在闲鱼 / 千牛及已学习分身前台时显示悬浮球。复制发货话术后点悬浮球即可解析激活码。", 14f, false).apply {
            setPadding(0, dp(8), 0, dp(16))
        })

        status = text("", 14f, false)
        root.addView(status)

        root.addView(button("打开无障碍设置") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(section("服务器"))
        baseUrl = input("HTTPS 服务器地址", config.baseUrl)
        root.addView(baseUrl)

        token = input("Bearer Token（可空）", tokenStore.read()).apply {
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
            config.renewHours = hours
            tokenStore.save(token.text.toString())
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
            renderStatus()
        })

        root.addView(section("目标应用 / 分身"))
        packageList = text("", 13f, false)
        root.addView(packageList)

        root.addView(button("学习闲鱼分身") { beginLearning("闲鱼分身") })
        root.addView(button("学习千牛分身") { beginLearning("千牛分身") })
        root.addView(text("学习时会记录你接下来切到的第一个外部 App 包名。开始学习后直接切回对应分身即可。", 12f, false))

        root.addView(section("已对接 activation-code-system"))
        root.addView(text(
            "查询：GET /backend/cards?q=<激活码>\n" +
                "激活：POST /backend/cards/{id}/activate\n" +
                "退款停用：POST /backend/cards/{id}/refund/disable\n" +
                "续期：POST /backend/cards/{id}/renew\n" +
                "解绑：POST /backend/cards/{id}/unbind",
            13f,
            false
        ))

        return ScrollView(this).apply { addView(root) }
    }

    private fun beginLearning(label: String) {
        registry.beginLearning(label)
        Toast.makeText(this, "请切到$label，助手会记录下一个外部 App", Toast.LENGTH_LONG).show()
        moveTaskToBack(true)
    }

    private fun renderStatus() {
        val serviceEnabled = LicenseAccessibilityService.INSTANCE != null
        status.text = if (serviceEnabled) "前台检测：已启用" else "前台检测：未启用"
        status.setTextColor(if (serviceEnabled) 0xFF1B7F3A.toInt() else 0xFFB3261E.toInt())
        packageList.text = registry.packages().sorted().joinToString(separator = "\n", prefix = "当前白名单：\n")
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
