package com.yasinjahantigh.logarhythm.playback

import android.bluetooth.BluetoothA2dp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
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
 * and provides direct per-player AudioDeviceInfo routing plus MediaRouter & system switcher fallback.
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
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(AudioOutputState())
    val state: StateFlow<AudioOutputState> = _state.asStateFlow()

    @Volatile
    var preferredDeviceId: Int? = null
        private set

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    private val routerCallback = object : MediaRouter.SimpleCallback() {
        override fun onRouteSelected(router: MediaRouter?, type: Int, info: MediaRouter.RouteInfo?) = refresh()
        override fun onRouteUnselected(router: MediaRouter?, type: Int, info: MediaRouter.RouteInfo?) = refresh()
        override fun onRouteAdded(router: MediaRouter?, info: MediaRouter.RouteInfo?) = refresh()
        override fun onRouteRemoved(router: MediaRouter?, info: MediaRouter.RouteInfo?) = refresh()
        override fun onRouteChanged(router: MediaRouter?, info: MediaRouter.RouteInfo?) = refresh()
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh()
        }
    }

    init {
        runCatching {
            audioManager?.registerAudioDeviceCallback(deviceCallback, mainHandler)
        }
        mainHandler.post {
            runCatching {
                mediaRouter?.addCallback(
                    MediaRouter.ROUTE_TYPE_LIVE_AUDIO,
                    routerCallback
                )
            }
        }
        runCatching {
            val filter = IntentFilter().apply {
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(AudioManager.ACTION_HEADSET_PLUG)
                addAction("android.media.VOLUME_CHANGED_ACTION")
            }
            ContextCompat.registerReceiver(context, systemReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        }
        refresh()
    }

    fun findOutputDeviceInfo(deviceId: Int): AudioDeviceInfo? {
        if (deviceId <= 0) return null
        val am = audioManager ?: return null
        return runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == deviceId }
        }.getOrNull()
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

        val candidates = btDevices + wiredDevices + usbDevices + speaker
        val currentPreferred = preferredDeviceId
        val validPreferred = currentPreferred?.takeIf { pref ->
            pref > 0 && candidates.any { it.id == pref }
        }
        if (currentPreferred != null && validPreferred == null) {
            preferredDeviceId = null
        }

        // Determine currently active media route from explicit player preference or MediaRouter + AudioManager state.
        val selectedRouteName = runCatching {
            mediaRouter?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)?.name?.toString()?.trim()
        }.getOrNull().orEmpty()
        val isSelectedDefaultSpeaker = runCatching {
            val selected = mediaRouter?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
            val def = mediaRouter?.defaultRoute
            selected != null && def != null && selected == def && !am.isWiredHeadsetOn && !am.isBluetoothA2dpOn
        }.getOrDefault(false)

        val activeDeviceId: Int = validPreferred ?: when {
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

        val ordered = candidates.map { dev ->
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
     * Selects an output device for ExoPlayer (AudioDeviceInfo routing) and attempts MediaRouter sync.
     * Returns true when direct per-player routing is supported for this device, or false when the
     * system media output dialog should be opened instead.
     */
    fun selectDevice(device: AudioOutputDevice): Boolean {
        val deviceInfo = findOutputDeviceInfo(device.id)
        preferredDeviceId = deviceInfo?.id

        val router = mediaRouter
        var routerSwitched = false
        if (router != null) {
            runCatching {
                when (device.kind) {
                    AudioOutputKind.SPEAKER -> {
                        val def = router.defaultRoute
                        if (def != null) {
                            router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, def)
                            routerSwitched = true
                        }
                    }
                    AudioOutputKind.BLUETOOTH -> {
                        val count = router.routeCount
                        for (i in 0 until count) {
                            val route = router.getRouteAt(i)
                            val rName = route.name?.toString()?.trim().orEmpty()
                            if (device.name.isNotBlank() && rName.equals(device.name, ignoreCase = true)) {
                                router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, route)
                                routerSwitched = true
                                break
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
        refresh()
        return deviceInfo != null || routerSwitched
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
