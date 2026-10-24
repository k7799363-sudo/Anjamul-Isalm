package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioOutputDetector
import com.example.model.AudioOutputDevice
import com.example.model.AudioOutputType
import com.example.ui.getAudioOutputIconRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AudioOutputDetectorTest {

    private lateinit var context: Context
    private lateinit var detector: AudioOutputDetector

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        detector = AudioOutputDetector(context)
    }

    @Test
    fun `test phone speaker is default when no external device active`() {
        val device = detector.currentDevice.value
        assertEquals(AudioOutputType.PHONE_SPEAKER, device.type)
        assertEquals(R.drawable.ic_audio_speaker, getAudioOutputIconRes(device.type))
        assertFalse(device.isWireless)
    }

    @Test
    fun `test Phone speaker to connect Buds to Buds icon`() {
        // Initial state: Phone Speaker
        assertEquals(AudioOutputType.PHONE_SPEAKER, detector.currentDevice.value.type)

        // Connect Buds (e.g. Realme Buds Air)
        val buds = AudioOutputDevice(
            type = AudioOutputType.BLUETOOTH_EARBUDS,
            name = "Realme Buds Air 3",
            isWireless = true,
            description = "Active via Bluetooth A2DP"
        )
        detector.simulateDevice(buds)

        assertEquals(AudioOutputType.BLUETOOTH_EARBUDS, detector.currentDevice.value.type)
        assertEquals("Realme Buds Air 3", detector.currentDevice.value.name)
        assertTrue(detector.currentDevice.value.isWireless)
        assertEquals(R.drawable.ic_audio_earbuds, getAudioOutputIconRes(detector.currentDevice.value.type))
    }

    @Test
    fun `test Buds to disconnect to Phone speaker icon`() {
        // Connect Buds
        val buds = AudioOutputDevice(
            type = AudioOutputType.BLUETOOTH_EARBUDS,
            name = "Galaxy Buds Pro",
            isWireless = true
        )
        detector.simulateDevice(buds)
        assertEquals(AudioOutputType.BLUETOOTH_EARBUDS, detector.currentDevice.value.type)

        // Disconnect Buds -> returns to phone speaker
        detector.simulateDevice(null)
        val fallback = detector.currentDevice.value
        assertEquals(AudioOutputType.PHONE_SPEAKER, fallback.type)
        assertEquals(R.drawable.ic_audio_speaker, getAudioOutputIconRes(fallback.type))
    }

    @Test
    fun `test Buds to wired headset to Headset icon`() {
        // Connect Buds
        val buds = AudioOutputDevice(
            type = AudioOutputType.BLUETOOTH_EARBUDS,
            name = "AirPods Pro",
            isWireless = true
        )
        detector.simulateDevice(buds)
        assertEquals(AudioOutputType.BLUETOOTH_EARBUDS, detector.currentDevice.value.type)

        // Plug in wired headset
        val wired = AudioOutputDevice(
            type = AudioOutputType.WIRED_HEADPHONES,
            name = "Wired Studio Headphones",
            isWireless = false,
            description = "Connected via 3.5mm audio jack"
        )
        detector.simulateDevice(wired)

        assertEquals(AudioOutputType.WIRED_HEADPHONES, detector.currentDevice.value.type)
        assertFalse(detector.currentDevice.value.isWireless)
        assertEquals(R.drawable.ic_audio_headphones, getAudioOutputIconRes(detector.currentDevice.value.type))
    }

    @Test
    fun `test Buds to Bluetooth speaker to Bluetooth speaker icon`() {
        // Connect Buds
        val buds = AudioOutputDevice(
            type = AudioOutputType.BLUETOOTH_EARBUDS,
            name = "Pixel Buds",
            isWireless = true
        )
        detector.simulateDevice(buds)
        assertEquals(AudioOutputType.BLUETOOTH_EARBUDS, detector.currentDevice.value.type)

        // Switch to Bluetooth Speaker (e.g. JBL Flip 6 / Soundcore)
        val btSpeaker = AudioOutputDevice(
            type = AudioOutputType.BLUETOOTH_SPEAKER,
            name = "JBL Flip 6 (Speaker)",
            isWireless = true,
            description = "Connected via Bluetooth Speaker A2DP"
        )
        detector.simulateDevice(btSpeaker)

        assertEquals(AudioOutputType.BLUETOOTH_SPEAKER, detector.currentDevice.value.type)
        assertTrue(detector.currentDevice.value.isWireless)
        assertEquals(R.drawable.ic_audio_bluetooth_speaker, getAudioOutputIconRes(detector.currentDevice.value.type))
    }

    @Test
    fun `test Android output switch updates icon immediately`() {
        // Music playing on Buds
        val buds = AudioOutputDevice(
            type = AudioOutputType.BLUETOOTH_EARBUDS,
            name = "Realme Buds Air",
            isWireless = true
        )
        detector.simulateDevice(buds)
        assertEquals(AudioOutputType.BLUETOOTH_EARBUDS, detector.currentDevice.value.type)

        // User toggles Android System Media Output Switcher to Phone Speaker
        val phoneSpeaker = AudioOutputDevice(
            type = AudioOutputType.PHONE_SPEAKER,
            name = "Phone Speaker",
            isWireless = false
        )
        detector.simulateDevice(phoneSpeaker)
        assertEquals(AudioOutputType.PHONE_SPEAKER, detector.currentDevice.value.type)
        assertEquals(R.drawable.ic_audio_speaker, getAudioOutputIconRes(detector.currentDevice.value.type))

        // User switches back to Buds
        detector.simulateDevice(buds)
        assertEquals(AudioOutputType.BLUETOOTH_EARBUDS, detector.currentDevice.value.type)
        assertEquals(R.drawable.ic_audio_earbuds, getAudioOutputIconRes(detector.currentDevice.value.type))
    }

    @Test
    fun `test background to reconnect or open app returns correct icon`() {
        // Simulate background resume refresh
        detector.refresh()
        val device = detector.currentDevice.value
        assertEquals(AudioOutputType.PHONE_SPEAKER, device.type)
        assertEquals(R.drawable.ic_audio_speaker, getAudioOutputIconRes(device.type))
    }

    @Test
    fun `test USB audio device mapping`() {
        val usb = AudioOutputDevice(
            type = AudioOutputType.USB_AUDIO,
            name = "USB-C Audio DAC",
            isWireless = false
        )
        detector.simulateDevice(usb)
        assertEquals(AudioOutputType.USB_AUDIO, detector.currentDevice.value.type)
        assertEquals(R.drawable.ic_audio_usb, getAudioOutputIconRes(detector.currentDevice.value.type))
    }
}
