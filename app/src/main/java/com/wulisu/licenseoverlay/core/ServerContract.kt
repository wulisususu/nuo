package com.wulisu.licenseoverlay.core

object ServerContract {
    const val QUERY_PATH = "/api/license/query"
    const val ACTION_PATH = "/api/license/action"
    const val FIELD_CODE = "code"
    const val FIELD_ACTION = "action"
    const val FIELD_DAYS = "days"
    val actionNames: Set<String> = LicenseAction.entries.map { it.wireName }.toSet()
}
