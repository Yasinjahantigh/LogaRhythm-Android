package com.yasinjahantigh.logarhythm.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class AudioOutputKind {
    SPEAKER,
    WIRED,
    USB,
    BLUETOOTH
}

data class AudioOutputDevice(
    val id: Int,
    val name: String,
    val kind: AudioOutputKind,
    val isActive: Boolean,
    val address: String = ""
)

data class AudioOutputState(
    val devices: List<AudioOutputDevice> = emptyList(),
    val activeKind: AudioOutputKind = AudioOutputKind.SPEAKER,
    val activeName: String = "",
    val volumeFraction: Float = 0.7f
)

/**
 * Observes real connected Android audio outputs (speaker, wired headset, USB DAC, Bluetooth A2DP/BLE)
 * and provides direct route switching via MediaRouter/AudioManager plus a one-tap launcher for
 * Android's native Media Output Switcher dialog.
 */
@Singleton
@Suppress("DEPRECATION")
class AudioOutputManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mediaRouter = runCatching {
        context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
    }.getOrNull()

    private val _state = MutableStateFlow(AudioOutputState())
    val state: StateFlow<AudioOutputState> = _state.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    init {
        runCatching {
            audioManager?.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        }
        refresh()
    }

    fun refresh() {
        val am = audioManager ?: return
        val outputs = runCatching { am.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull().orEmpty()

        val btDevices = mutableListOf<AudioOutputDevice>()
        val wiredDevices = mutableListOf<AudioOutputDevice>()
        val usbDevices = mutableListOf<AudioOutputDevice>()
        var speakerDevice: AudioOutputDevice? = null

        val seenKeys = HashSet<String>()
        for (info in outputs) {
            val rawName = info.productName?.toString()?.trim().orEmpty()
            val addr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.address.orEmpty() else ""
            when (info.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_HEARING_AID -> {
                    val key = "bt:${addr.ifBlank { rawName.lowercase() }}"
                    if (seenKeys.add(key)) {
                        btDevices += AudioOutputDevice(
                            id = info.id,
                            name = rawName,
                            kind = AudioOutputKind.BLUETOOTH,
                            isActive = false,
                            address = addr
                        )
                    }
                }
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                    if (seenKeys.add("wired")) {
                        wiredDevices += AudioOutputDevice(
                            id = info.id,
                            name = rawName,
                            kind = AudioOutputKind.WIRED,
                            isActive = false
                        )
                    }
                }
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> {
                    val key = "usb:${rawName.lowercase()}"
                    if (seenKeys.add(key)) {
                        usbDevices += AudioOutputDevice(
                            id = info.id,
                            name = rawName,
                            kind = AudioOutputKind.USB,
                            isActive = false
                        )
                    }
                }
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                    if (speakerDevice == null) {
                        speakerDevice = AudioOutputDevice(
                            id = info.id,
                            name = "",
                            kind = AudioOutputKind.SPEAKER,
                            isActive = false
                        )
                    }
                }
            }
        }

        val speaker = speakerDevice ?: AudioOutputDevice(
            id = -1,
            name = "",
            kind = AudioOutputKind.SPEAKER,
            isActive = false
        )

        // Determine currently active media route from MediaRouter + AudioManager state.
        val selectedRouteName = runCatching {
            mediaRouter?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)?.name?.toString()?.trim()
        }.getOrNull().orEmpty()
        val isSelectedDefaultSpeaker = runCatching {
            val selected = mediaRouter?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
            val def = mediaRouter?.defaultRoute
            selected != null && def != null && selected == def && !am.isWiredHeadsetOn && !am.isBluetoothA2dpOn
        }.getOrDefault(false)

        val activeDeviceId: Int = when {
            isSelectedDefaultSpeaker -> speaker.id
            btDevices.isNotEmpty() && (am.isBluetoothA2dpOn || selectedRouteName.isNotBlank()) -> {
                btDevices.firstOrNull { d ->
                    d.name.isNotBlank() && selectedRouteName.equals(d.name, ignoreCase = true)
                }?.id ?: btDevices.first().id
            }
            wiredDevices.isNotEmpty() -> wiredDevices.first().id
            usbDevices.isNotEmpty() -> usbDevices.first().id
            btDevices.isNotEmpty() -> btDevices.first().id
            else -> speaker.id
        }

        val ordered = (btDevices + wiredDevices + usbDevices + speaker).map { dev ->
            dev.copy(isActive = dev.id == activeDeviceId)
        }
        val active = ordered.firstOrNull { it.isActive } ?: speaker

        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxVol)

        _state.value = AudioOutputState(
            devices = ordered,
            activeKind = active.kind,
            activeName = active.name,
            volumeFraction = curVol.toFloat() / maxVol.toFloat()
        )
    }

    fun setMediaVolume(fraction: Float) {
        val am = audioManager ?: return
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = (fraction.coerceIn(0f, 1f) * maxVol).toInt().coerceIn(0, maxVol)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
        _state.value = _state.value.copy(volumeFraction = target.toFloat() / maxVol.toFloat())
    }

    /**
     * Attempts to switch the live audio route directly via MediaRouter / AudioManager.
     * Returns true if a direct route selection succeeded, or false if the system media output
     * dialog should be shown to complete the switch (required on Android 11+ for A2DP switching).
     */
    fun selectDevice(device: AudioOutputDevice): Boolean {
        val router = mediaRouter
        var switchedDirectly = false
        if (router != null) {
            runCatching {
                when (device.kind) {
                    AudioOutputKind.SPEAKER -> {
                        val def = router.defaultRoute
                        if (def != null) {
                            router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, def)
                            switchedDirectly = true
                        }
                    }
                    AudioOutputKind.BLUETOOTH -> {
                        val count = router.routeCount
                        for (i in 0 until count) {
                            val route = router.getRouteAt(i)
                            val rName = route.name?.toString()?.trim().orEmpty()
                            if (device.name.isNotBlank() && rName.equals(device.name, ignoreCase = true)) {
                                router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, route)
                                switchedDirectly = true
                                break
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        refresh()
        // On Android 11+, switching between Bluetooth A2DP and Phone Speaker is governed by SystemUI.
        // If our direct call did not move the active device to the requested one, let the UI open the native switcher.
        val nowActive = _state.value.devices.firstOrNull { it.isActive }
        return switchedDirectly && nowActive?.id == device.id
    }

    /**
     * Opens Android's native Media Output Switcher dialog (Android 11+), with automatic fallback
     * to the Volume Settings Panel (Android 10+) or Bluetooth Settings.
     */
    fun openSystemOutputSwitcher(activityContext: Context): Boolean {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(
                    Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG").apply {
                        setPackage("com.android.systemui")
                        putExtra("package_name", activityContext.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(
                    Intent(Settings.Panel.ACTION_VOLUME).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            add(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
        for (intent in candidates) {
            val launched = runCatching {
                activityContext.startActivity(intent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }
        return false
    }
}
