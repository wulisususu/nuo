package com.wulisu.licenseoverlay.api

data class LicenseSnapshot(
    val code: String,
    val status: String = "unknown",
    val expiresAt: String? = null,
    val message: String = ""
)

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val message: String, val httpCode: Int? = null) : ApiResult<Nothing>
}
