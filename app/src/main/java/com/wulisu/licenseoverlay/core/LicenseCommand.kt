package com.wulisu.licenseoverlay.core

enum class LicenseAction(val wireName: String, val destructive: Boolean) {
    ACTIVATE("activate", false),
    REFUND_DISABLE("refund_disable", true),
    RENEW("renew", false),
    UNBIND("unbind", true)
}

data class LicenseCommand(
    val code: String,
    val action: LicenseAction,
    val renewHours: Int? = null
) {
    init {
        require(code.matches(Regex("\\d{4,20}"))) { "activation code must be 4-20 digits" }
        if (action == LicenseAction.RENEW) {
            require(renewHours != null && renewHours in 1..999) {
                "renew requires hours in 1..999; 999 means permanent"
            }
        } else {
            require(renewHours == null) { "renewHours is only valid for renew" }
        }
    }
}
