package com.example.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AudioOutputDevice
import com.example.model.AudioOutputType
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GoldPrimary

/**
 * Returns the appropriate drawable resource ID representing the active hardware audio output.
 */
fun getAudioOutputIconRes(type: AudioOutputType): Int {
    return when (type) {
        AudioOutputType.BLUETOOTH_EARBUDS -> R.drawable.ic_audio_earbuds
        AudioOutputType.PHONE_SPEAKER -> R.drawable.ic_audio_speaker
        AudioOutputType.BLUETOOTH_HEADPHONES -> R.drawable.ic_audio_headphones
        AudioOutputType.BLUETOOTH_SPEAKER -> R.drawable.ic_audio_bluetooth_speaker
        AudioOutputType.WIRED_HEADPHONES -> R.drawable.ic_audio_headphones
        AudioOutputType.USB_AUDIO -> R.drawable.ic_audio_usb
        AudioOutputType.OTHER -> R.drawable.ic_audio_speaker
    }
}

/**
 * Dynamic Smart Audio Output Icon.
 * Smoothly morphs/transitions in real-time when the active output hardware changes
 * (e.g. from Phone Speaker to Wireless Earbuds like Realme Buds the moment they connect).
 */
@Composable
fun SmartAudioOutputIcon(
    device: AudioOutputDevice,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
    tint: Color = GoldPrimary
) {
    AnimatedContent(
        targetState = device.type,
        transitionSpec = {
            (scaleIn(initialScale = 0.82f, animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
             fadeIn(animationSpec = androidx.compose.animation.core.tween(220))) togetherWith
            (scaleOut(targetScale = 0.82f, animationSpec = androidx.compose.animation.core.tween(180, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
             fadeOut(animationSpec = androidx.compose.animation.core.tween(180)))
        },
        label = "audio_output_icon_morph"
    ) { type ->
        val iconRes = getAudioOutputIconRes(type)
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = device.name,
            tint = tint,
            modifier = modifier.size(iconSize)
        )
    }
}

/**
 * Tactile Smart Audio Output Chip.
 * Displays the dynamic icon + active device name with an animated connection dot.
 * Clicking it opens the hardware routing & device switcher bottom sheet.
 */
@Composable
fun SmartAudioOutputChip(
    device: AudioOutputDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val haptic = rememberHapticController()
    val isWireless = device.isWireless

    GlassCard(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.tap()
                onClick()
            }
            .testTag("smart_audio_output_chip"),
        shape = RoundedCornerShape(16.dp),
        borderColor = if (isWireless) EmeraldMint.copy(alpha = 0.45f) else GoldPrimary.copy(alpha = 0.30f),
        backgroundColor = if (isWireless) EmeraldMint.copy(alpha = 0.08f) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Morphing Output Icon
            SmartAudioOutputIcon(
                device = device,
                iconSize = if (compact) 16.dp else 18.dp,
                tint = if (isWireless) EmeraldMint else GoldPrimary
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Device Name
            Text(
                text = if (compact) {
                    if (device.type == AudioOutputType.BLUETOOTH_EARBUDS) "Earbuds"
                    else if (device.type == AudioOutputType.PHONE_SPEAKER) "Speaker"
                    else device.name.take(12)
                } else {
                    device.name
                },
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isWireless) EmeraldMint else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Active Green Dot for Wireless / Connected state
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isWireless) EmeraldMint else GoldPrimary.copy(alpha = 0.6f))
            )
        }
    }
}

/**
 * Interactive Audio Output Routing Modal Bottom Sheet.
 * Displays details of the active output device, hardware routing status,
 * options to simulate devices for testing, and a link to Android's Bluetooth/Sound settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartAudioOutputModalSheet(
    currentDevice: AudioOutputDevice,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSimulateDevice: (AudioOutputDevice?) -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberHapticController()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Audio Output Routing",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Dynamic hardware media routing",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // System Settings button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            haptic.tap()
                            openAudioSettings(context)
                        }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Bluetooth Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Current Active Hardware Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                borderColor = if (currentDevice.isWireless) EmeraldMint else GoldPrimary,
                backgroundColor = (if (currentDevice.isWireless) EmeraldMint else GoldPrimary).copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                (if (currentDevice.isWireless) EmeraldMint else GoldPrimary).copy(alpha = 0.22f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        SmartAudioOutputIcon(
                            device = currentDevice,
                            iconSize = 30.dp,
                            tint = if (currentDevice.isWireless) EmeraldMint else GoldPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentDevice.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldMint.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE ROUTE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldMint
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = currentDevice.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Hardware Routing Simulation & Testing",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Realme Buds Air (Wireless Earbuds)
            AudioRouteItem(
                title = "Realme Buds Air (Wireless Earbuds)",
                subtitle = "Simulate connecting Bluetooth TWS earbuds",
                iconRes = R.drawable.ic_audio_earbuds,
                isSelected = currentDevice.type == AudioOutputType.BLUETOOTH_EARBUDS && currentDevice.name.contains("Realme", ignoreCase = true),
                onClick = {
                    haptic.confirm()
                    onSimulateDevice(
                        AudioOutputDevice(
                            type = AudioOutputType.BLUETOOTH_EARBUDS,
                            name = "Realme Buds Air",
                            isWireless = true,
                            description = "Connected via Bluetooth A2DP / Low-latency stereo"
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Built-in Phone Speaker
            AudioRouteItem(
                title = "Phone Speaker",
                subtitle = "Simulate disconnecting earbuds back to internal speaker",
                iconRes = R.drawable.ic_audio_speaker,
                isSelected = currentDevice.type == AudioOutputType.PHONE_SPEAKER,
                onClick = {
                    haptic.confirm()
                    onSimulateDevice(
                        AudioOutputDevice(
                            type = AudioOutputType.PHONE_SPEAKER,
                            name = "Phone Speaker",
                            isWireless = false,
                            description = "Internal phone stereo speaker"
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Wired Studio Headphones
            AudioRouteItem(
                title = "Wired Headphones",
                subtitle = "Simulate 3.5mm analog wired headset connected",
                iconRes = R.drawable.ic_audio_headphones,
                isSelected = currentDevice.type == AudioOutputType.WIRED_HEADPHONES,
                onClick = {
                    haptic.confirm()
                    onSimulateDevice(
                        AudioOutputDevice(
                            type = AudioOutputType.WIRED_HEADPHONES,
                            name = "Wired Headphones",
                            isWireless = false,
                            description = "Connected via 3.5mm analog audio"
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Bluetooth Speaker
            AudioRouteItem(
                title = "Bluetooth Speaker (JBL Flip / Boombox)",
                subtitle = "Simulate external wireless Bluetooth loudspeaker",
                iconRes = R.drawable.ic_audio_bluetooth_speaker,
                isSelected = currentDevice.type == AudioOutputType.BLUETOOTH_SPEAKER,
                onClick = {
                    haptic.confirm()
                    onSimulateDevice(
                        AudioOutputDevice(
                            type = AudioOutputType.BLUETOOTH_SPEAKER,
                            name = "JBL Flip 6 (Speaker)",
                            isWireless = true,
                            description = "Connected via Bluetooth Speaker A2DP"
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. USB-C Audio Interface
            AudioRouteItem(
                title = "USB Audio Device",
                subtitle = "Simulate USB-C digital DAC / audio interface",
                iconRes = R.drawable.ic_audio_usb,
                isSelected = currentDevice.type == AudioOutputType.USB_AUDIO,
                onClick = {
                    haptic.confirm()
                    onSimulateDevice(
                        AudioOutputDevice(
                            type = AudioOutputType.USB_AUDIO,
                            name = "USB-C Audio DAC",
                            isWireless = false,
                            description = "High-res audio connected via USB-C"
                        )
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Live Automatic Hardware Detection
            AudioRouteItem(
                title = "Auto Live Hardware Detection",
                subtitle = "Query Android AudioManager in real-time",
                iconRes = R.drawable.ic_audio_bluetooth,
                isSelected = false,
                isReset = true,
                onClick = {
                    haptic.confirm()
                    onSimulateDevice(null) // Clears simulation, restores live hardware listener
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AudioRouteItem(
    title: String,
    subtitle: String,
    iconRes: Int,
    isSelected: Boolean,
    isReset: Boolean = false,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        borderColor = if (isSelected) EmeraldMint else null,
        backgroundColor = if (isSelected) EmeraldMint.copy(alpha = 0.12f) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) EmeraldMint.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = if (isSelected) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) EmeraldMint else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = EmeraldMint,
                    modifier = Modifier.size(20.dp)
                )
            } else if (isReset) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(GoldPrimary.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                }
            }
        }
    }
}

private fun openAudioSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
