package com.wulisu.licenseoverlay.api

import com.wulisu.licenseoverlay.core.LicenseCommand
import com.wulisu.licenseoverlay.core.ServerContract
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection

class LicenseApi(
    private val baseUrlProvider: () -> String,
    private val tokenProvider: () -> String
) {
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val sequence = AtomicLong(0)

    fun query(code: String, callback: (requestId: Long, ApiResult<LicenseSnapshot>) -> Unit): Long {
        val requestId = sequence.incrementAndGet()
        execute(requestId, ServerContract.QUERY_PATH, JSONObject().put(ServerContract.FIELD_CODE, code), callback)
        return requestId
    }

    fun execute(command: LicenseCommand, callback: (requestId: Long, ApiResult<LicenseSnapshot>) -> Unit): Long {
        val body = JSONObject()
            .put(ServerContract.FIELD_CODE, command.code)
            .put(ServerContract.FIELD_ACTION, command.action.wireName)
        command.days?.let { body.put(ServerContract.FIELD_DAYS, it) }
        val requestId = sequence.incrementAndGet()
        execute(requestId, ServerContract.ACTION_PATH, body, callback)
        return requestId
    }

    fun close() = executor.shutdownNow()

    private fun execute(requestId: Long, path: String, body: JSONObject, callback: (Long, ApiResult<LicenseSnapshot>) -> Unit) {
        executor.execute {
            val result = runCatching { perform(path, body) }
                .getOrElse { ApiResult.Failure(it.message ?: "network error") }
            callback(requestId, result)
        }
    }

    private fun perform(path: String, body: JSONObject): ApiResult<LicenseSnapshot> {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (!baseUrl.startsWith("https://", ignoreCase = true)) return ApiResult.Failure("请先配置 HTTPS 服务器地址")
        val connection = (URL(baseUrl + path).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 8_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            tokenProvider().trim().takeIf { it.isNotEmpty() }?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val httpCode = connection.responseCode
            val stream = if (httpCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (httpCode !in 200..299) return ApiResult.Failure(extractMessage(text).ifBlank { "HTTP $httpCode" }, httpCode)
            return parseSuccess(body.optString(ServerContract.FIELD_CODE), text)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSuccess(code: String, text: String): ApiResult<LicenseSnapshot> {
        if (text.isBlank()) return ApiResult.Success(LicenseSnapshot(code = code, message = "操作成功"))
        val json = JSONObject(text)
        val success = when {
            json.has("success") -> json.optBoolean("success")
            json.has("ok") -> json.optBoolean("ok")
            else -> true
        }
        val message = json.optString("message", json.optString("msg", ""))
        if (!success) return ApiResult.Failure(message.ifBlank { "服务器拒绝操作" })
        val data = json.optJSONObject("data") ?: json
        return ApiResult.Success(LicenseSnapshot(
            code = data.optString("code", code),
            status = data.optString("status", data.optString("state", "unknown")),
            expiresAt = firstNonBlank(data.optString("expiresAt"), data.optString("expire_at"), data.optString("expires_at")),
            message = message
        ))
    }

    private fun extractMessage(text: String): String = runCatching {
        val json = JSONObject(text)
        json.optString("message", json.optString("msg", json.optString("error", "")))
    }.getOrDefault("")

    private fun firstNonBlank(vararg values: String): String? = values.firstOrNull { it.isNotBlank() }
}
