package org.huamitoken

/** A paired watch/band with its Bluetooth pairing key. Ports huami_token/models.py. */
data class Device(
    val mac: String,
    val active: Boolean,
    val authKey: String,
) {
    companion object {
        fun fromApiResponse(itemJson: String): Device {
            val mac = JsonLite.stringField(itemJson, "macAddress") ?: "??:??:??:??:??:??"
            val active = (JsonLite.longField(itemJson, "activeStatus") ?: 0L) != 0L
            val additionalInfo = JsonLite.stringField(itemJson, "additionalInfo") ?: "{}"
            val authKey = JsonLite.stringField(additionalInfo, "auth_key") ?: "??"
            return Device(mac = mac, active = active, authKey = authKey)
        }
    }
}

/** A Xiaomi bound source (wearable) with its Bluetooth key. */
data class XiaomiSource(
    val name: String,
    val mac: String,
    val authKey: String,
) {
    companion object {
        fun fromApiResponse(sourceJson: String): XiaomiSource {
            val name = (JsonLite.stringField(sourceJson, "name") ?: "Unknown").trim()
            val detailRaw = JsonLite.objectField(sourceJson, "detail")
                ?: JsonLite.stringField(sourceJson, "detail")
                ?: "{}"
            val mac = JsonLite.stringField(detailRaw, "mac")
                ?: JsonLite.stringField(sourceJson, "mac")
                ?: "??:??:??:??:??:??"
            val authKey = JsonLite.stringField(detailRaw, "auth_key") ?: ""
            return XiaomiSource(name = name, mac = mac, authKey = authKey)
        }
    }
}

/** UI-friendly row shown on every non-CLI target. */
data class DeviceDisplay(
    val title: String,
    val mac: String,
    val active: String?,
    val key: String,
)
