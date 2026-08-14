package com.wulisu.licenseoverlay.api

import com.wulisu.licenseoverlay.core.LicenseAction
import com.wulisu.licenseoverlay.core.LicenseCommand
import com.wulisu.licenseoverlay.core.ServerContract
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
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
        executor.execute {
            val result: ApiResult<LicenseSnapshot> = try {
                when (val resolved = resolveCode(code)) {
                    is ApiResult.Success -> ApiResult.Success(resolved.value.snapshot)
                    is ApiResult.Failure -> resolved
                }
            } catch (t: Throwable) {
                ApiResult.Failure(t.message ?: "network error")
            }
            callback(requestId, result)
        }
        return requestId
    }

    fun execute(command: LicenseCommand, callback: (requestId: Long, ApiResult<LicenseSnapshot>) -> Unit): Long {
        val requestId = sequence.incrementAndGet()
        executor.execute {
            val result: ApiResult<LicenseSnapshot> = try {
                when (val resolved = resolveCode(command.code)) {
                    is ApiResult.Failure -> resolved
                    is ApiResult.Success -> when (val target = resolved.value) {
                        is ResolvedCode.Stock -> ApiResult.Failure(
                            "该激活码仍在库存表（${target.snapshot.status}），尚未转换为正式授权，不能执行此动作"
                        )
                        is ResolvedCode.Backend -> performAction(target.id, command)
                    }
                }
            } catch (t: Throwable) {
                ApiResult.Failure(t.message ?: "network error")
            }
            callback(requestId, result)
        }
        return requestId
    }

    fun close() = executor.shutdownNow()

    private fun resolveCode(code: String): ApiResult<ResolvedCode> {
        val encoded = URLEncoder.encode(code, Charsets.UTF_8.name())
        val backendPath = "${ServerContract.BACKEND_LIST_PATH}?q=$encoded&limit=20&offset=0"
        when (val response = performJson("GET", backendPath, null)) {
            is ApiResult.Failure -> return response
            is ApiResult.Success -> {
                val exact = findExact(response.value.optJSONArray("items") ?: JSONArray(), code)
                if (exact.size > 1) return ApiResult.Failure("找到多个完全相同的正式授权记录，请到后台检查重复数据")
                if (exact.size == 1) {
                    val item = exact.first()
                    val id = item.optInt("id", -1)
                    if (id <= 0) return ApiResult.Failure("服务器返回的 card_id 无效")
                    return ApiResult.Success(ResolvedCode.Backend(id, snapshotFromBackend(item, code, "查询成功")))
                }
            }
        }

        val stockPath = "${ServerContract.STOCK_LIST_PATH}?q=$encoded&page=1&page_size=20"
        return when (val response = performJson("GET", stockPath, null)) {
            is ApiResult.Failure -> response
            is ApiResult.Success -> {
                val exact = findExact(response.value.optJSONArray("items") ?: JSONArray(), code)
                when {
                    exact.size > 1 -> ApiResult.Failure("找到多个完全相同的库存记录，请到后台检查重复数据")
                    exact.size == 1 -> ApiResult.Success(
                        ResolvedCode.Stock(snapshotFromStock(exact.first(), code))
                    )
                    else -> ApiResult.Failure("未找到激活码 $code", 404)
                }
            }
        }
    }

    private fun performAction(cardId: Int, command: LicenseCommand): ApiResult<LicenseSnapshot> {
        val body = when (command.action) {
            LicenseAction.ACTIVATE -> JSONObject()
            LicenseAction.REFUND_DISABLE -> JSONObject()
            LicenseAction.RENEW -> JSONObject().put("hours", command.renewHours)
            LicenseAction.UNBIND -> null
        }
        return when (val response = performJson("POST", ServerContract.actionPath(cardId, command.action), body)) {
            is ApiResult.Failure -> response
            is ApiResult.Success -> ApiResult.Success(
                snapshotFromBackend(response.value, command.code, actionSuccessMessage(command))
            )
        }
    }

    private fun actionSuccessMessage(command: LicenseCommand): String = when (command.action) {
        LicenseAction.ACTIVATE -> "激活成功"
        LicenseAction.REFUND_DISABLE -> "退款停用成功"
        LicenseAction.RENEW -> if (command.renewHours == 999) "已转为永久授权" else "已续期 ${command.renewHours} 小时"
        LicenseAction.UNBIND -> "解绑成功"
    }

    private fun findExact(items: JSONArray, code: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (item.optString("card_secret") == code || item.optString("card_no") == code) result += item
        }
        return result
    }

    private fun snapshotFromBackend(json: JSONObject, fallbackCode: String, message: String): LicenseSnapshot {
        val resolvedCode = json.optString("card_secret").takeIf { it.isNotBlank() }
            ?: json.optString("card_no").takeIf { it.isNotBlank() }
            ?: fallbackCode
        return LicenseSnapshot(
            code = resolvedCode,
            status = json.optString("status", "unknown"),
            expiresAt = nullableString(json, "expires_at"),
            bindingStatus = nullableString(json, "binding_status"),
            machineCode = nullableString(json, "machine_code"),
            source = "backend",
            message = message
        )
    }

    private fun snapshotFromStock(json: JSONObject, fallbackCode: String): LicenseSnapshot {
        val resolvedCode = json.optString("card_secret").takeIf { it.isNotBlank() }
            ?: json.optString("card_no").takeIf { it.isNotBlank() }
            ?: fallbackCode
        return LicenseSnapshot(
            code = resolvedCode,
            status = json.optString("status", "unknown"),
            source = "stock",
            message = "库存码，尚未转换为正式授权"
        )
    }

    private fun performJson(method: String, path: String, body: JSONObject?): ApiResult<JSONObject> {
        val baseUrl = baseUrlProvider().trim().trimEnd('/')
        if (!baseUrl.startsWith("https://", ignoreCase = true)) return ApiResult.Failure("请先配置 HTTPS 服务器地址")
        val connection = (URL(baseUrl + path).openConnection() as HttpsURLConnection).apply {
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            tokenProvider().trim().takeIf { it.isNotEmpty() }?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val httpCode = connection.responseCode
            val stream = if (httpCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (httpCode !in 200..299) {
                return ApiResult.Failure(extractMessage(text).ifBlank { "HTTP $httpCode" }, httpCode)
            }
            if (text.isBlank()) return ApiResult.Success(JSONObject())
            return runCatching { ApiResult.Success(JSONObject(text)) }
                .getOrElse { ApiResult.Failure("服务器返回了无法解析的 JSON") }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractMessage(text: String): String = runCatching {
        val json = JSONObject(text)
        val detail = json.opt("detail")
        when {
            json.optString("message").isNotBlank() -> json.optString("message")
            json.optString("msg").isNotBlank() -> json.optString("msg")
            json.optString("error").isNotBlank() -> json.optString("error")
            detail != null && detail != JSONObject.NULL -> detail.toString()
            else -> ""
        }
    }.getOrDefault("")

    private fun nullableString(json: JSONObject, key: String): String? {
        val value = json.opt(key)
        if (value == null || value == JSONObject.NULL) return null
        return value.toString().takeIf { it.isNotBlank() && it != "null" }
    }

    private sealed interface ResolvedCode {
        val snapshot: LicenseSnapshot
        data class Backend(val id: Int, override val snapshot: LicenseSnapshot) : ResolvedCode
        data class Stock(override val snapshot: LicenseSnapshot) : ResolvedCode
    }
}
