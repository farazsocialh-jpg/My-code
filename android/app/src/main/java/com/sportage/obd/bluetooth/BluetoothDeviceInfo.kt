package com.sportage.obd.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Permission-safe accessors for [BluetoothDevice] metadata.
 *
 * On Android 12 (API 31) and newer, reading a device name or MAC address requires the
 * runtime permission [Manifest.permission.BLUETOOTH_CONNECT]. Every accessor below
 * performs that check explicitly so callers never crash when the permission has not
 * been granted yet (for example before the user answers the permission prompt).
 */
object BluetoothDeviceInfo {

    /**
     * True when this process may read [BluetoothDevice] properties such as name or address.
     */
    fun canReadDeviceInfo(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Device name, or [fallback] when the name is unknown or cannot be read.
     */
    fun displayName(context: Context, device: BluetoothDevice, fallback: String = "ELM327"): String {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        return if (granted) try { device.name?.takeIf { it.isNotBlank() } ?: fallback }
        catch (_: SecurityException) { fallback } else fallback
    }

    /**
     * Device MAC address, or null when the name/address cannot be read.
     */
    fun address(context: Context, device: BluetoothDevice): String? {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        return if (granted) try { device.address } catch (_: SecurityException) { null } else null
    }

    /**
     * Human readable "Name (MAC)" label, or a generic label when metadata is unavailable.
     */
    fun label(context: Context, device: BluetoothDevice): String {
        val address = address(context, device) ?: return "Bluetooth device"
        return "${displayName(context, device, "Device")} ($address)"
    }
}
