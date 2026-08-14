package com.wulisu.licenseoverlay.config

import android.content.Context

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("license_overlay_config", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "")?.trim().orEmpty()
        set(value) = prefs.edit().putString("base_url", value.trim().trimEnd('/')).apply()

    var renewHours: Int
        get() {
            if (prefs.contains("renew_hours")) return prefs.getInt("renew_hours", 720).coerceIn(1, 999)
            val legacyDays = prefs.getInt("renew_days", 30).coerceIn(1, 41)
            return (legacyDays * 24).coerceAtMost(998)
        }
        set(value) = prefs.edit()
            .putInt("renew_hours", value.coerceIn(1, 999))
            .remove("renew_days")
            .apply()
}
