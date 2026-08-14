package com.wulisu.licenseoverlay.config

import android.content.Context

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("license_overlay_config", Context.MODE_PRIVATE)
    var baseUrl: String
        get() = prefs.getString("base_url", "")?.trim().orEmpty()
        set(value) = prefs.edit().putString("base_url", value.trim().trimEnd('/')).apply()
    var renewDays: Int
        get() = prefs.getInt("renew_days", 30).coerceIn(1, 3650)
        set(value) = prefs.edit().putInt("renew_days", value.coerceIn(1, 3650)).apply()
}
