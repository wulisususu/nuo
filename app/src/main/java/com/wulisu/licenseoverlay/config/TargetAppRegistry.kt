package com.wulisu.licenseoverlay.config

import android.content.Context

class TargetAppRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("target_apps", Context.MODE_PRIVATE)
    fun packages(): Set<String> = prefs.getStringSet("packages", null)?.takeIf { it.isNotEmpty() }?.toSet() ?: DEFAULT_PACKAGES
    fun isTarget(packageName: String): Boolean = packageName in packages()
    fun add(packageName: String) { if (packageName.isNotBlank()) prefs.edit().putStringSet("packages", (packages() + packageName).toSet()).apply() }
    fun beginLearning(label: String) { prefs.edit().putString("learn_label", label).apply() }
    fun pendingLearningLabel(): String? = prefs.getString("learn_label", null)
    fun completeLearning(packageName: String): String? {
        val label = pendingLearningLabel() ?: return null
        add(packageName)
        prefs.edit().remove("learn_label").apply()
        return label
    }
    companion object {
        val DEFAULT_PACKAGES = setOf("com.taobao.idlefish", "com.taobao.qianniu")
    }
}
