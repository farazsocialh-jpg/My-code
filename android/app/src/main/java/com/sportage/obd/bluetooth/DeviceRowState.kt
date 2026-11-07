package com.sportage.obd.bluetooth

/** Address-scoped presentation shared by the paired and BLE lists. */
data class DeviceRowState(val label: String, val enabled: Boolean, val connected: Boolean)

fun ConnectionState.rowState(address: String?): DeviceRowState {
    val hasAddress = !address.isNullOrBlank()
    return when (this) {
        is ConnectionState.Connecting -> DeviceRowState(
            if (hasAddress && this.address.equals(address, true)) "Connecting..." else "Connect",
            false, false
        )
        is ConnectionState.Connected -> {
            val matches = hasAddress && this.address.equals(address, true)
            DeviceRowState(if (matches) "Disconnect" else "Connect", hasAddress, matches)
        }
        else -> DeviceRowState("Connect", hasAddress, false)
    }
}
