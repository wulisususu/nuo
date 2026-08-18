package com.wulisu.licenseoverlay.core

enum class DetectedGame(
    val scope: String,
    val displayName: String,
    val appIds: List<String>
) {
    ZZZ(
        scope = "ZZZ",
        displayName = "绝区零",
        appIds = listOf("zzz-remielle", "zzz")
    ),
    WUWA(
        scope = "WUWA",
        displayName = "鸣潮",
        appIds = listOf("wuwa-zigrika-commercial", "wuwa")
    )
}

object GameDetector {
    private val zzzKeywords = listOf(
        "绝区零",
        "zenless zone zero",
        "zenless",
        "zzz",
        "remielle",
        "蕾米埃尔"
    )

    private val wuwaKeywords = listOf(
        "鸣潮",
        "wuthering waves",
        "wuthering",
        "wuwa",
        "zigrika"
    )

    fun detect(text: String): DetectedGame? {
        if (text.isBlank()) return null
        val normalized = text.lowercase()
        val zzzScore = zzzKeywords.count { normalized.contains(it) }
        val wuwaScore = wuwaKeywords.count { normalized.contains(it) }
        return when {
            zzzScore == 0 && wuwaScore == 0 -> null
            zzzScore > wuwaScore -> DetectedGame.ZZZ
            wuwaScore > zzzScore -> DetectedGame.WUWA
            else -> null
        }
    }
}
