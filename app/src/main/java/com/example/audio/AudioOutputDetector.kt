package com.example.audio

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.AudioOutputDevice
import com.example.model.AudioOutputType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Smart Audio Output Routing Detector.
 * Dynamically detects active hardware audio output (Wireless Earbuds, Headphones, Phone Speaker, etc.)
 * in real-time using:
 * 1. ExoPlayer's underlying AudioTrack routedDevice information via AudioRouting.OnRoutingChangedListener
 * 2. Android 13+ getAudioDevicesForAttributes for authoritative OS media routing
 * 3. AudioManager AudioDeviceCallback for dynamic hardware plug/unplug events
 * 4. Complementary system broadcasts (A2DP active device changes, headset plug, noisy events, ACL connect/disconnect)
 */
class AudioOutputDetector(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentDevice = MutableStateFlow(defaultPhoneSpeaker())
    val currentDevice: StateFlow<AudioOutputDevice> = _currentDevice.asStateFlow()

    private var simulatedDevice: AudioOutputDevice? = null

    // Reference to active player AudioTrack and its routing listener
    private var boundAudioTrack: AudioTrack? = null
    private var routingListener: AudioRouting.OnRoutingChangedListener? = null

    // AudioDeviceCallback for dynamic real-time hardware plug/unplug events
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices added: ${addedDevices?.map { "${it.type}:${safeProductName(it)}" }}")
            // A new physical device was plugged in or connected (e.g. Buds or Wired Headset)
            simulatedDevice = null
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed: ${removedDevices?.map { "${it.type}:${safeProductName(it)}" }}")
            // System hardware removal overrides temporary simulation
            simulatedDevice = null
            refresh()
        }
    }

    // Broadcast receiver for instantaneous wired headset, Bluetooth connection & routing changes
    private val audioEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            Log.d(TAG, "Received audio routing broadcast: $action")
            if (action == AudioManager.ACTION_AUDIO_BECOMING_NOISY ||
                action == "android.bluetooth.device.action.ACL_DISCONNECTED" ||
                (action == Intent.ACTION_HEADSET_PLUG && intent.getIntExtra("state", -1) == 0)
            ) {
                // Earbuds / Bluetooth / Headset disconnected! Instantly reset simulation and re-detect
                simulatedDevice = null
            }
            refresh()
        }
    }

    init {
        // Initial detection
        _currentDevice.value = detectActiveOutput()
        registerListeners()
    }

    private fun registerListeners() {
        try {
            audioManager?.registerAudioDeviceCallback(
                audioDeviceCallback,
                mainHandler
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not register AudioDeviceCallback: ${e.message}")
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction("android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED")
                addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
                addAction("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED")
                addAction("android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED")
                addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
                addAction("android.bluetooth.adapter.action.STATE_CHANGED")
                addAction("android.bluetooth.device.action.ACL_CONNECTED")
                addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
                addAction("android.media.action.HDMI_AUDIO_PLUG_STATUS")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(audioEventReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(audioEventReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register audio broadcast receiver: ${e.message}")
        }
    }

    /**
     * Binds the actual AudioTrack from ExoPlayer's audio sink to monitor live OS routing changes.
     * When Android routes audio to Bluetooth Buds, Wired Headset, or Phone Speaker,
     * the AudioRouting.OnRoutingChangedListener fires immediately.
     */
    fun bindPlayerAudioTrack(track: AudioTrack) {
        try {
            // Remove previous routing listener if any
            unbindPlayerAudioTrack()

            boundAudioTrack = track

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val listener = AudioRouting.OnRoutingChangedListener { router ->
                    val routed = router.routedDevice
                    Log.i(TAG, "ExoPlayer AudioTrack routedDevice changed: ${routed?.type} -> ${safeProductName(routed)}")
                    onAudioTrackRouted(routed)
                }
                routingListener = listener
                track.addOnRoutingChangedListener(listener, mainHandler)
            }

            // Immediately inspect the currently routed device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val currentRouted = track.routedDevice
                if (currentRouted != null) {
                    onAudioTrackRouted(currentRouted)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error binding player AudioTrack: ${e.message}")
        }
    }

    /**
     * Unbinds the AudioTrack routing listener when playback stops or player is re-initialized.
     */
    fun unbindPlayerAudioTrack() {
        try {
            val track = boundAudioTrack
            val listener = routingListener
            if (track != null && listener != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                track.removeOnRoutingChangedListener(listener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding player AudioTrack: ${e.message}")
        } finally {
            boundAudioTrack = null
            routingListener = null
        }
    }

    /**
     * Called whenever ExoPlayer's underlying AudioTrack changes its physical routed device.
     */
    fun onAudioTrackRouted(device: AudioDeviceInfo?) {
        if (simulatedDevice != null) return
        if (device == null) {
            refresh()
            return
        }

        val mapped = mapAudioDeviceInfo(device)
        if (mapped != null && _currentDevice.value != mapped) {
            Log.i(TAG, "Playback engine routed device updated: ${mapped.type} -> ${mapped.name}")
            _currentDevice.value = mapped
        }
    }

    /**
     * Re-queries the active audio device and updates the StateFlow.
     */
    fun refresh() {
        if (simulatedDevice != null) {
            _currentDevice.value = simulatedDevice!!
            return
        }
        val detected = detectActiveOutput()
        if (_currentDevice.value != detected) {
            Log.i(TAG, "Active audio route updated: ${detected.type} -> ${detected.name}")
            _currentDevice.value = detected
        }
    }

    /**
     * Inspects audio routing to determine the current active media device.
     * Uses:
     * 1. ExoPlayer's AudioTrack routed device if playing
     * 2. Android 13+ getAudioDevicesForAttributes for authoritative media output route
     * 3. Connected devices from AudioManager.getDevices(GET_DEVICES_OUTPUTS) prioritizing:
     *    - Wired headphones / headset
     *    - USB audio
     *    - Bluetooth audio (Buds, Headphones, Speaker)
     *    - Phone speaker default
     */
    fun detectActiveOutput(): AudioOutputDevice {
        val am = audioManager ?: return defaultPhoneSpeaker()

        // 1. If the player's AudioTrack is actively routed, check its routedDevice directly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val trackDevice = boundAudioTrack?.routedDevice
                if (trackDevice != null) {
                    val mapped = mapAudioDeviceInfo(trackDevice)
                    if (mapped != null) {
                        return mapped
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading bound AudioTrack routedDevice: ${e.message}")
            }
        }

        // 2. Android 13+ (API 33+): Query the OS Audio Policy engine for the exact active media route
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val mediaAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                val routedDevices = am.getAudioDevicesForAttributes(mediaAttributes)
                if (!routedDevices.isNullOrEmpty()) {
                    val primary = routedDevices.first()
                    // If the OS routed to an external device, that is 100% authoritative
                    if (isExternalAudioDevice(primary)) {
                        val mapped = mapAudioDeviceInfo(primary)
                        if (mapped != null) {
                            return mapped
                        }
                    }
                    // If the OS routed to built-in speaker, check if Bluetooth is actually connected
                    // but permission was missing (causing getAudioDevicesForAttributes to redact Bluetooth)
                    if (!hasBluetoothConnectPermission() && hasConnectedBluetoothOutput(am)) {
                        val btDevice = getConnectedBluetoothDevice(am)
                        if (btDevice != null) {
                            val mapped = mapAudioDeviceInfo(btDevice)
                            if (mapped != null) return mapped
                        }
                    } else if (hasBluetoothConnectPermission()) {
                        // If permission is present and the OS explicitly routed media to speaker, return speaker
                        val mapped = mapAudioDeviceInfo(primary)
                        if (mapped != null) {
                            return mapped
                        }
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException in getAudioDevicesForAttributes: ${e.message}")
            } catch (e: Exception) {
                Log.w(TAG, "getAudioDevicesForAttributes check failed: ${e.message}")
            }
        }

        // 3. Fallback / Universal Output Hardware Inspection
        try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

            // Priority 1: Wired Headset / Headphones (3.5mm jack or line out)
            val wiredDevice = devices.firstOrNull { dev ->
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                dev.type == AudioDeviceInfo.TYPE_LINE_DIGITAL
            }
            if (wiredDevice != null) {
                val mapped = mapAudioDeviceInfo(wiredDevice)
                if (mapped != null) return mapped
            }

            // Priority 2: USB Audio (USB-C headphones, DAC, interface)
            val usbDevice = devices.firstOrNull { dev ->
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            if (usbDevice != null) {
                val mapped = mapAudioDeviceInfo(usbDevice)
                if (mapped != null) return mapped
            }

            // Priority 3: Bluetooth Audio Device (Earbuds / Headphones / Bluetooth Speaker)
            val btDevice = devices.firstOrNull { dev ->
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                dev.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
            if (btDevice != null) {
                val mapped = mapAudioDeviceInfo(btDevice)
                if (mapped != null) return mapped
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting audio output: ${e.message}")
        }

        // Default: Built-in Phone Speaker
        return defaultPhoneSpeaker()
    }

    private fun isExternalAudioDevice(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> false
            else -> true
        }
    }

    private fun hasConnectedBluetoothOutput(am: AudioManager): Boolean {
        return try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { dev ->
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                dev.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getConnectedBluetoothDevice(am: AudioManager): AudioDeviceInfo? {
        return try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.firstOrNull { dev ->
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                dev.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
        } catch (_: Exception) {
            null
        }
    }

    fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun safeProductName(device: AudioDeviceInfo?): String {
        if (device == null) return ""
        return try {
            device.productName?.toString().orEmpty().trim()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading productName: ${e.message}")
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun defaultPhoneSpeaker(): AudioOutputDevice {
        return AudioOutputDevice(
            type = AudioOutputType.PHONE_SPEAKER,
            name = "Phone Speaker",
            isWireless = false,
            description = "Built-in stereo speaker"
        )
    }

    /**
     * Maps an Android AudioDeviceInfo to our domain AudioOutputDevice.
     */
    fun mapAudioDeviceInfo(device: AudioDeviceInfo): AudioOutputDevice? {
        val rawName = safeProductName(device)

        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> {
                defaultPhoneSpeaker()
            }

            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> {
                AudioOutputDevice(
                    type = AudioOutputType.WIRED_HEADPHONES,
                    name = if (rawName.isNotBlank()) rawName else "Wired Headphones",
                    isWireless = false,
                    description = "Connected via 3.5mm audio jack"
                )
            }

            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> {
                AudioOutputDevice(
                    type = AudioOutputType.USB_AUDIO,
                    name = if (rawName.isNotBlank()) rawName else "USB Audio Device",
                    isWireless = false,
                    description = "High-res audio connected via USB-C"
                )
            }

            AudioDeviceInfo.TYPE_BLE_SPEAKER -> {
                AudioOutputDevice(
                    type = AudioOutputType.BLUETOOTH_SPEAKER,
                    name = if (rawName.isNotBlank()) rawName else "Bluetooth Speaker",
                    isWireless = true,
                    description = "Connected via Bluetooth Speaker"
                )
            }

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID -> {
                if (isBluetoothSpeaker(rawName, device.type)) {
                    AudioOutputDevice(
                        type = AudioOutputType.BLUETOOTH_SPEAKER,
                        name = if (rawName.isNotBlank()) rawName else "Bluetooth Speaker",
                        isWireless = true,
                        description = "Connected via Bluetooth Speaker"
                    )
                } else if (isHeadphones(rawName)) {
                    AudioOutputDevice(
                        type = AudioOutputType.BLUETOOTH_HEADPHONES,
                        name = if (rawName.isNotBlank() && !rawName.equals("Bluetooth", ignoreCase = true)) rawName else "Bluetooth Headphones",
                        isWireless = true,
                        description = "Connected via Bluetooth Headphones"
                    )
                } else {
                    // Default for personal wireless listening audio devices (AirPods, Realme Buds, Galaxy Buds, etc.)
                    AudioOutputDevice(
                        type = AudioOutputType.BLUETOOTH_EARBUDS,
                        name = if (rawName.isNotBlank() && !rawName.equals("Bluetooth", ignoreCase = true)) rawName else "Wireless Earbuds",
                        isWireless = true,
                        description = "Active via Bluetooth A2DP / Low-latency stereo"
                    )
                }
            }

            else -> null
        }
    }

    /**
     * Determines whether the device name or type indicates a Bluetooth speaker.
     */
    private fun isBluetoothSpeaker(name: String, deviceType: Int): Boolean {
        if (deviceType == AudioDeviceInfo.TYPE_BLE_SPEAKER) return true
        val lower = name.lowercase()
        val speakerKeywords = listOf(
            "speaker", "soundbar", "boombox", "charge", "flip", "xtreme", "clip",
            "go 2", "go 3", "go 4", "soundlink", "revolve", "megaboom", "wonderboom",
            "pill", "echo", "nest", "homepod", "soundcore 2", "soundcore 3", "motion",
            "soundcore flare", "tribit", "anker soundcore", "soundbox", "audiobox",
            "partybox", "emberton", "stanmore", "acton", "woburn", "kilburn", "willen",
            "dock", "tv", "receiver", "soundfreaq", "braven", "bose color"
        )
        return speakerKeywords.any { lower.contains(it) }
    }

    /**
     * Determines whether the device name indicates over-ear / on-ear headphones.
     */
    private fun isHeadphones(name: String): Boolean {
        val lower = name.lowercase()
        val headphoneKeywords = listOf(
            "wh-1000", "quietcomfort", "bose 700", "beats studio", "beats solo",
            "major iv", "major v", "crusher", "anc", "over-ear", "on-ear", "headphone", "headset",
            "momentum 4", "sony wh", "space q", "life q"
        )
        return headphoneKeywords.any { lower.contains(it) }
    }

    /**
     * Allows switching to a simulated output device (for testing Realme Buds vs Phone Speaker vs Wired vs Bluetooth Speaker).
     */
    fun simulateDevice(device: AudioOutputDevice?) {
        simulatedDevice = device
        _currentDevice.value = device ?: detectActiveOutput()
    }

    fun release() {
        unbindPlayerAudioTrack()

        try {
            audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (_: Exception) {}

        try {
            context.unregisterReceiver(audioEventReceiver)
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "AudioOutputDetector"
    }
}
