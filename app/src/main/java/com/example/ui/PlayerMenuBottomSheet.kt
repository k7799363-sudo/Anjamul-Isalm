package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioPreset
import com.example.model.AudioTrack
import com.example.model.PlaybackState
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GoldPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuBottomSheet(
    track: AudioTrack,
    playbackState: PlaybackState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSetAudioPreset: (AudioPreset) -> Unit,
    onUpdateTrackDate: (String, Long) -> Unit = { _, _ -> },
    onDeleteTrack: (AudioTrack) -> Unit = {}
) {
    val haptic = rememberHapticController()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showShareMessage by remember { mutableStateOf(false) }

    // Track's custom sort timestamp state (initialized from track.customSortTimestamp or track.dateAdded)
    val initialTimestamp = remember(track.id, track.customSortTimestamp, track.dateAdded) {
        when {
            track.customSortTimestamp > 0L -> track.customSortTimestamp
            track.dateAdded > 0L -> track.dateAdded
            else -> System.currentTimeMillis()
        }
    }
    var activeSortTimestamp by remember(track.id, initialTimestamp) {
        mutableLongStateOf(initialTimestamp)
    }

    // Chained DatePicker -> TimePicker dialog states
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var pendingSelectedYear by remember { mutableStateOf(2026) }
    var pendingSelectedMonth by remember { mutableStateOf(0) }
    var pendingSelectedDay by remember { mutableStateOf(1) }

    val formattedDateTimeAdded = remember(activeSortTimestamp) {
        val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        formatter.format(Date(activeSortTimestamp))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = Color(0xFF07120E).copy(alpha = 0.95f),
        tonalElevation = 16.dp,
        modifier = Modifier.testTag("player_menu_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Cancel button matching reference image 3
            Text(
                text = "Cancel",
                color = Color(0xFFE57373),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable {
                        haptic.tap()
                        onDismiss()
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .testTag("menu_cancel_btn")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 1. Sleep Timer
            val sleepTimerSubtitle = if (playbackState.isSleepTimerActive && playbackState.sleepTimerRemainingSec != null) {
                "Active • ${(playbackState.sleepTimerRemainingSec + 59) / 60}m remaining (Hard stop)"
            } else {
                null
            }

            MenuItemRow(
                icon = Icons.Outlined.Schedule,
                title = "Sleep Timer",
                subtitle = sleepTimerSubtitle,
                onClick = {
                    haptic.tap()
                    showSleepTimerDialog = true
                }
            )

            // 2. Speed
            MenuItemRow(
                icon = Icons.Outlined.FastForward,
                title = "Speed",
                subtitle = "${playbackState.playbackSpeed}x",
                onClick = {
                    haptic.tap()
                    showSpeedDialog = true
                }
            )

            // 3. Equalizer / Spiritual Acoustics
            MenuItemRow(
                icon = Icons.Outlined.Equalizer,
                title = "Equalizer",
                subtitle = playbackState.audioPreset.title,
                onClick = {
                    haptic.tap()
                    showEqualizerDialog = true
                }
            )

            // 4. Add to Playlist
            MenuItemRow(
                icon = Icons.Outlined.PlaylistAdd,
                title = "Add to Playlist",
                onClick = {
                    haptic.tap()
                    onDismiss()
                }
            )

            // 5. Song Info
            MenuItemRow(
                icon = Icons.Outlined.Info,
                title = "Song Info",
                subtitle = "${track.bitrateKbps} kbps • $formattedDateTimeAdded",
                onClick = {
                    haptic.tap()
                    showInfoDialog = true
                }
            )

            // 6. Set as Ringtone
            MenuItemRow(
                icon = Icons.Outlined.Notifications,
                title = "Set as Ringtone",
                onClick = {
                    haptic.tap()
                    showShareMessage = true
                }
            )

            // 7. Share file
            MenuItemRow(
                icon = Icons.Outlined.Share,
                title = "Share file",
                onClick = {
                    haptic.tap()
                    showShareMessage = true
                }
            )

            // 8. Delete from Device (Permanent physical storage deletion)
            MenuItemRow(
                icon = Icons.Outlined.DeleteForever,
                title = "Delete from Device",
                subtitle = "Permanently remove file from physical storage",
                titleColor = Color(0xFFEF5350),
                iconTint = Color(0xFFEF5350),
                onClick = {
                    haptic.tap()
                    onDeleteTrack(track)
                }
            )
        }
    }

    // Interactive Radial Sleep Timer Sheet (Hard Stop)
    if (showSleepTimerDialog) {
        RadialSleepTimerSheet(
            isActive = playbackState.isSleepTimerActive,
            remainingSeconds = playbackState.sleepTimerRemainingSec,
            onSetTimer = { minutes ->
                onSetSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                onSetSleepTimer(0)
                showSleepTimerDialog = false
            },
            onDismissRequest = {
                showSleepTimerDialog = false
            }
        )
    }

    // Speed Dialog
    if (showSpeedDialog) {
        val speeds = listOf(0.75f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f)
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    speeds.forEach { spd ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.tap()
                                    onSetPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playbackState.playbackSpeed == spd,
                                onClick = {
                                    haptic.tap()
                                    onSetPlaybackSpeed(spd)
                                    showSpeedDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "${spd}x", fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Equalizer / Audio Mode Dialog
    if (showEqualizerDialog) {
        AlertDialog(
            onDismissRequest = { showEqualizerDialog = false },
            title = { Text("Acoustic Sanctuary Equalizer", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    AudioPreset.values().forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.tap()
                                    onSetAudioPreset(preset)
                                    showEqualizerDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = playbackState.audioPreset == preset,
                                onClick = {
                                    haptic.tap()
                                    onSetAudioPreset(preset)
                                    showEqualizerDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = preset.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = preset.subtitle,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEqualizerDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Song Info / Audio Track Details Dialog with "Date and Time Added" section
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = Color(0xFF07120E).copy(alpha = 0.95f),
            tonalElevation = 16.dp,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(28.dp)
                )
                .testTag("song_info_dialog"),
            title = {
                Text(
                    text = "Audio Track Details",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoDetailRow("Title", track.title)
                    InfoDetailRow("Reciter / Artist", track.reciter)
                    InfoDetailRow("Album", track.album)
                    InfoDetailRow("Folder", track.folderName)
                    InfoDetailRow("Bitrate", "${track.bitrateKbps} kbps (Lossless/High MP3)")
                    InfoDetailRow("Sample Rate", "${track.sampleRateHz} Hz")
                    if (track.sizeBytes > 0) {
                        InfoDetailRow("File Size", String.format(Locale.US, "%.2f MB", track.sizeBytes / 1048576.0))
                    }
                    if (track.ayahCount != null) {
                        InfoDetailRow("Ayah Count", "${track.ayahCount} Verses")
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    Spacer(modifier = Modifier.height(4.dp))

                    // Visually distinct Glassmorphic "Date and Time Added" Section
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0x26FFFFFF),
                                        Color(0x0DFFFFFF)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        GoldPrimary.copy(alpha = 0.55f),
                                        EmeraldMint.copy(alpha = 0.25f)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("song_info_date_time_section")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarToday,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Date and Time Added",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = GoldPrimary.copy(alpha = 0.9f),
                                        letterSpacing = 0.4.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formattedDateTimeAdded,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.testTag("song_info_formatted_date_text")
                                    )
                                }
                            }

                            // Elegant Edit (Pencil) Icon Button to launch chained Date & Time pickers
                            IconButton(
                                onClick = {
                                    haptic.tap()
                                    val cal = Calendar.getInstance().apply {
                                        timeInMillis = activeSortTimestamp
                                    }
                                    pendingSelectedYear = cal.get(Calendar.YEAR)
                                    pendingSelectedMonth = cal.get(Calendar.MONTH)
                                    pendingSelectedDay = cal.get(Calendar.DAY_OF_MONTH)
                                    showDatePickerDialog = true
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.10f))
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.45f), CircleShape)
                                    .testTag("edit_track_date_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Date and Time Added",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK", color = GoldPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Step 3a: DatePickerDialog (First step of the chained picker)
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = activeSortTimestamp
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.confirm()
                        val selectedUtcMillis = datePickerState.selectedDateMillis ?: activeSortTimestamp
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = selectedUtcMillis
                        }
                        pendingSelectedYear = utcCal.get(Calendar.YEAR)
                        pendingSelectedMonth = utcCal.get(Calendar.MONTH)
                        pendingSelectedDay = utcCal.get(Calendar.DAY_OF_MONTH)

                        // Chain immediately into TimePickerDialog after confirming the date
                        showDatePickerDialog = false
                        showTimePickerDialog = true
                    },
                    modifier = Modifier.testTag("confirm_date_picker_btn")
                ) {
                    Text("Next: Time", color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = Color(0xFF0A1612)
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color(0xFF0A1612),
                    titleContentColor = GoldPrimary,
                    headlineContentColor = Color.White,
                    weekdayContentColor = Color.White.copy(alpha = 0.6f),
                    subheadContentColor = Color.White.copy(alpha = 0.7f),
                    dayContentColor = Color.White,
                    selectedDayContainerColor = GoldPrimary,
                    selectedDayContentColor = Color(0xFF050B09),
                    todayContentColor = EmeraldMint,
                    todayDateBorderColor = EmeraldMint
                )
            )
        }
    }

    // Step 3b: Chained TimePickerDialog (Launched immediately after Date confirmation)
    if (showTimePickerDialog) {
        val initialCal = remember(activeSortTimestamp) {
            Calendar.getInstance().apply { timeInMillis = activeSortTimestamp }
        }
        val timePickerState = rememberTimePickerState(
            initialHour = initialCal.get(Calendar.HOUR_OF_DAY),
            initialMinute = initialCal.get(Calendar.MINUTE),
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            containerColor = Color(0xFF0A1612),
            title = {
                Text(
                    text = "Select Time Added",
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(
                        state = timePickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = Color(0xFF122620),
                            clockDialSelectedContentColor = Color(0xFF050B09),
                            clockDialUnselectedContentColor = Color.White,
                            selectorColor = GoldPrimary,
                            containerColor = Color(0xFF0A1612),
                            periodSelectorBorderColor = GoldPrimary.copy(alpha = 0.5f),
                            periodSelectorSelectedContainerColor = GoldPrimary,
                            periodSelectorUnselectedContainerColor = Color(0xFF122620),
                            periodSelectorSelectedContentColor = Color(0xFF050B09),
                            periodSelectorUnselectedContentColor = Color.White,
                            timeSelectorSelectedContainerColor = GoldPrimary.copy(alpha = 0.25f),
                            timeSelectorUnselectedContainerColor = Color(0xFF122620),
                            timeSelectorSelectedContentColor = GoldPrimary,
                            timeSelectorUnselectedContentColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.confirm()
                        // Convert selected Year, Month, Day, Hour, and Minute into Unix epoch milliseconds
                        val combinedCalendar = Calendar.getInstance().apply {
                            set(Calendar.YEAR, pendingSelectedYear)
                            set(Calendar.MONTH, pendingSelectedMonth)
                            set(Calendar.DAY_OF_MONTH, pendingSelectedDay)
                            set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(Calendar.MINUTE, timePickerState.minute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        val newTimestamp = combinedCalendar.timeInMillis
                        activeSortTimestamp = newTimestamp
                        showTimePickerDialog = false

                        // Step 4: Save to Room DB & immediately re-sort "Newest" and "Oldest" filters
                        onUpdateTrackDate(track.id, newTimestamp)
                    },
                    modifier = Modifier.testTag("confirm_time_picker_btn")
                ) {
                    Text("Save Date & Time", color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    if (showShareMessage) {
        AlertDialog(
            onDismissRequest = { showShareMessage = false },
            title = { Text("Spiritual Audio Ready") },
            text = { Text("Audio track '${track.title}' is loaded and ready for sharing or setting as personalized reminder.") },
            confirmButton = {
                TextButton(onClick = { showShareMessage = false }) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color? = null,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint ?: MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = if (titleColor != null) FontWeight.Medium else FontWeight.Normal,
                color = titleColor ?: MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = GoldPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun InfoDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
