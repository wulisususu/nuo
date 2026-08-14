package com.wulisu.licenseoverlay.core

sealed interface ParseResult {
    data class Found(val code: String) : ParseResult
    data class Ambiguous(val codes: List<String>) : ParseResult
    data object NotFound : ParseResult
}

object ActivationCodeParser {
    private val labeled = Regex("(?:激活码|卡密|授权码)\\s*[:：]?\\s*(\\d{4,20})", setOf(RegexOption.IGNORE_CASE))
    private val standalone = Regex("(?<!\\d)\\d{6,12}(?!\\d)")
    private val noisyLabels = listOf("解压密码", "密码", "手机号", "手机", "QQ", "订单号", "订单")

    fun parse(text: String): ParseResult {
        if (text.isBlank()) return ParseResult.NotFound
        val labeledCodes = labeled.findAll(text).map { it.groupValues[1] }.distinct().toList()
        if (labeledCodes.size == 1) return ParseResult.Found(labeledCodes.first())
        if (labeledCodes.size > 1) return ParseResult.Ambiguous(labeledCodes)
        val candidates = standalone.findAll(text).filterNot { isLikelyNoise(text, it.range.first) }.map { it.value }.distinct().toList()
        return when (candidates.size) { 0 -> ParseResult.NotFound; 1 -> ParseResult.Found(candidates.first()); else -> ParseResult.Ambiguous(candidates) }
    }

    private fun isLikelyNoise(text: String, start: Int): Boolean {
        val prefix = text.substring(maxOf(0, start - 12), start).replace("\\s+".toRegex(), "")
        return noisyLabels.any { prefix.endsWith(it) || prefix.endsWith("$it：") || prefix.endsWith("$it:") }
    }
}
