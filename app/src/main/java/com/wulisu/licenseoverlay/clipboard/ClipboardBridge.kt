package com.wulisu.licenseoverlay.clipboard

import android.content.Context
import android.content.Intent

object ClipboardBridge {
    @Volatile var active: Boolean = false
        private set
    @Volatile private var callback: ((String?) -> Unit)? = null

    fun request(context: Context, onResult: (String?) -> Unit) {
        callback = onResult
        active = true
        runCatching {
            context.startActivity(Intent(context, ClipboardBridgeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            })
        }.onFailure { deliver(null) }
    }

    fun deliver(text: String?) {
        active = false
        callback?.invoke(text)
        callback = null
    }
}
