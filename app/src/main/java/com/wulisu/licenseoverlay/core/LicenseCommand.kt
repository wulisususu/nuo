package com.wulisu.licenseoverlay.core

enum class LicenseAction(val wireName: String, val destructive: Boolean) {
    ACTIVATE("activate", false),
    REFUND_DISABLE("refund_disable", true),
    RENEW("renew", false),
    UNBIND("unbind", true)
}

data class LicenseCommand(val code: String, val action: LicenseAction, val days: Int? = null) {
    init {
        require(code.matches(Regex("\\d{4,20}"))) { "activation code must be 4-20 digits" }
        if (action == LicenseAction.RENEW) require(days != null && days in 1..3650) { "renew requires days in 1..3650" }
        else require(days == null) { "days is only valid for renew" }
    }
}
