package com.wulisu.licenseoverlay.core

object ServerContract {
    const val BACKEND_LIST_PATH = "/backend/cards"
    const val STOCK_LIST_PATH = "/api/test-card-stock/list"

    fun actionPath(cardId: Int, action: LicenseAction): String = when (action) {
        LicenseAction.ACTIVATE -> "/backend/cards/$cardId/activate"
        LicenseAction.REFUND_DISABLE -> "/backend/cards/$cardId/refund/disable"
        LicenseAction.RENEW -> "/backend/cards/$cardId/renew"
        LicenseAction.UNBIND -> "/backend/cards/$cardId/unbind"
    }

    val actionNames: Set<String> = LicenseAction.entries.map { it.wireName }.toSet()
}
