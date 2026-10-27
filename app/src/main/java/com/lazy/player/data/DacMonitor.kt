package com.lazy.player.data

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*

/** USB DAC presence detection only; do not confuse presence with bit-perfect routing. */
@Composable
fun rememberUsbDacConnected(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var connected by remember { mutableStateOf(isUsbPresent(manager)) }
    DisposableEffect(manager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                connected = isUsbPresent(manager)
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                connected = isUsbPresent(manager)
            }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        connected = isUsbPresent(manager)
        onDispose { manager.unregisterAudioDeviceCallback(callback) }
    }
    return connected
}

private fun isUsbPresent(manager: AudioManager): Boolean =
    manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
        it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
    }
