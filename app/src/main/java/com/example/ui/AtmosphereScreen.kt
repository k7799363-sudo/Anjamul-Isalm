package com.example.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ambient.AmbientDownloadState
import com.example.data.ambient.DownloadedAmbientEntity
import com.example.data.ambient.OnlineAmbientSound
import com.example.model.PlaybackState
import com.example.ui.theme.EmeraldMint
import com.example.ui.theme.GoldPrimary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtmosphereScreen(
    playbackState: PlaybackState,
    downloadedAmbients: List<DownloadedAmbientEntity> = emptyList(),
    favoriteAmbients: List<DownloadedAmbientEntity> = emptyList(),
    recentAmbients: List<DownloadedAmbientEntity> = emptyList(),
    totalStorageBytes: Long = 0L,
    searchQuery: String = "",
    isSearching: Boolean = false,
    searchResults: List<OnlineAmbientSound> = emptyList(),
    searchError: String? = null,
    previewingSound: OnlineAmbientSound? = null,
    onSearchQueryChange: (String) -> Unit = {},
    onPreviewSound: (OnlineAmbientSound) -> Unit = {},
    onPlayDownloaded: (DownloadedAmbientEntity) -> Unit = {},
    onDownloadSound: (OnlineAmbientSound) -> Unit = {},
    onDeleteDownloaded: (String) -> Unit = {},
    onToggleFavorite: (String, Boolean) -> Unit = { _, _ -> },
    onImportCustomAudio: (Uri, String) -> Unit = { _, _ -> },
    getDownloadProgress: (String) -> StateFlow<Int> = { MutableStateFlow(0) },
    getDownloadState: (String) -> StateFlow<AmbientDownloadState> = { MutableStateFlow(AmbientDownloadState.NOT_DOWNLOADED) },
    onClose: () -> Unit,
    onToggleAmbientPlayback: () -> Unit,
    onAmbientVolumeChange: (Float) -> Unit,
    onRecitationVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onLeaveScreen: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = rememberHapticController()
    var selectedMyTab by remember { mutableIntStateOf(0) } // 0: All Custom, 1: Favorites, 2: Recent
    var showManageStorageSheet by remember { mutableStateOf(false) }

    // Pending URI from GetContent("audio/*") waiting for user-defined name confirmation
    var pendingPickedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var customAtmosphereNameInput by remember { mutableStateOf("") }

    // ActivityResultContracts.GetContent() filtering strictly for audio/*
    val customAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            var extractedName = "Custom Atmosphere ${downloadedAmbients.size + 1}"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        val rawName = cursor.getString(nameIndex)
                        if (!rawName.isNullOrBlank()) {
                            extractedName = rawName.substringBeforeLast(".")
                        }
                    }
                }
            } catch (_: Exception) {}
            customAtmosphereNameInput = extractedName
            pendingPickedAudioUri = uri
        }
    }

    val storageFormatted = remember(totalStorageBytes) {
        val mb = totalStorageBytes / (1024f * 1024f)
        if (mb >= 1024f) {
            String.format("%.2f GB", mb / 1024f)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onLeaveScreen()
        }
    }

    BackHandler {
        onClose()
    }

    IslamicSacredBackground {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            haptic.tap()
                            onClose()
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Custom Atmosphere",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Local Audio Upload & Infinite Loop Engine",
                            fontSize = 12.sp,
                            color = GoldPrimary
                        )
                    }
                }

                // Storage Indicator Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22FFFFFF))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable {
                            haptic.tap()
                            showManageStorageSheet = true
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Storage",
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = storageFormatted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. LOCAL AUDIO FILE UPLOAD CARD (ActivityResultContracts.GetContent("audio/*"))
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Import Local Atmosphere Audio",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Select any audio/* file from your device. It will be copied into internal storage for permanent offline playback and continuous infinite looping.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(GoldPrimary, EmeraldMint)
                                        )
                                    )
                                    .clickable {
                                        haptic.confirm()
                                        customAudioPickerLauncher.launch("audio/*")
                                    }
                                    .padding(vertical = 14.dp)
                                    .testTag("upload_custom_atmosphere_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Upload Audio File",
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Select Local Audio File (audio/*)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. ATMOSPHERE DUAL MIXER CONTROLS
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Mixer",
                                        tint = GoldPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Dual Audio Layer Mixer",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        val activeTitle = playbackState.activeAmbientTitle ?: "No track selected"
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Repeat,
                                                contentDescription = "Infinite Loop",
                                                tint = EmeraldMint,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (playbackState.isAmbientPlaying) "Looping: $activeTitle" else activeTitle,
                                                fontSize = 11.sp,
                                                color = if (playbackState.isAmbientPlaying) EmeraldMint else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        haptic.tap()
                                        onToggleAmbientPlayback()
                                    },
                                    modifier = Modifier.testTag("toggle_ambient_playback_button")
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isAmbientPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Toggle Atmosphere",
                                        tint = GoldPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Music Volume Layer
                            Text(
                                text = "Primary Music Volume: ${(playbackState.recitationVolume * 100).toInt()}%",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = playbackState.recitationVolume,
                                onValueChange = onRecitationVolumeChange,
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = GoldPrimary,
                                    activeTrackColor = GoldPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Atmosphere Volume Layer
                            Text(
                                text = "Custom Atmosphere Volume: ${(playbackState.ambientVolume * 100).toInt()}%",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = playbackState.ambientVolume,
                                onValueChange = onAmbientVolumeChange,
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = EmeraldMint,
                                    activeTrackColor = EmeraldMint
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Custom atmosphere tracks run on the secondary ExoPlayer with REPEAT_MODE_ALL so they loop infinitely alongside your music.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // 3. MY CUSTOM ATMOSPHERE LIBRARY (ROOM DB)
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Saved Custom Atmosphere Tracks",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Text(
                                text = "${downloadedAmbients.size} saved",
                                fontSize = 13.sp,
                                color = GoldPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TabRow(
                            selectedTabIndex = selectedMyTab,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ) {
                            Tab(
                                selected = selectedMyTab == 0,
                                onClick = {
                                    haptic.tap()
                                    selectedMyTab = 0
                                },
                                text = { Text("All (${downloadedAmbients.size})") }
                            )
                            Tab(
                                selected = selectedMyTab == 1,
                                onClick = {
                                    haptic.tap()
                                    selectedMyTab = 1
                                },
                                text = { Text("Favorites (${favoriteAmbients.size})") }
                            )
                            Tab(
                                selected = selectedMyTab == 2,
                                onClick = {
                                    haptic.tap()
                                    selectedMyTab = 2
                                },
                                text = { Text("Recent") }
                            )
                        }
                    }
                }

                val currentList = when (selectedMyTab) {
                    0 -> downloadedAmbients
                    1 -> favoriteAmbients
                    else -> recentAmbients
                }

                if (currentList.isEmpty()) {
                    item {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AudioFile,
                                    contentDescription = "Empty Custom Atmosphere",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (selectedMyTab == 0) "No Custom Atmosphere Tracks Yet" else "No Tracks in This View",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Tap 'Select Local Audio File (audio/*)' above to import any audio file into internal storage for infinite background looping.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(currentList, key = { it.id }) { downloaded ->
                        val isTrackLooping = playbackState.isAmbientPlaying &&
                            (playbackState.activePreviewSoundId == downloaded.id || playbackState.activeAmbientFilePath == downloaded.localFilePath)

                        DownloadedSoundCard(
                            entity = downloaded,
                            isPlaying = isTrackLooping,
                            onPlay = { onPlayDownloaded(downloaded) },
                            onToggleFavorite = { onToggleFavorite(downloaded.id, downloaded.isFavorite) },
                            onDelete = { onDeleteDownloaded(downloaded.id) }
                        )
                    }
                }
            }
        }
    }

    // Dialog to name the custom uploaded audio file before copying to internal storage & saving to Room
    pendingPickedAudioUri?.let { pickedUri ->
        AlertDialog(
            onDismissRequest = { pendingPickedAudioUri = null },
            containerColor = Color(0xFF101D19),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Text(
                    text = "Name Custom Atmosphere",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a name for this atmosphere track. The audio file will be copied into internal app storage and saved to Room.",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customAtmosphereNameInput,
                        onValueChange = { customAtmosphereNameInput = it },
                        singleLine = true,
                        label = { Text("Atmosphere Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = GoldPrimary,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_atmosphere_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = customAtmosphereNameInput.trim().ifBlank { "Custom Atmosphere" }
                        onImportCustomAudio(pickedUri, finalName)
                        pendingPickedAudioUri = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("confirm_save_atmosphere_button")
                ) {
                    Text("Save & Loop", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingPickedAudioUri = null }
                ) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            }
        )
    }

    // Manage Storage Sheet
    if (showManageStorageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManageStorageSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Manage Atmosphere Storage",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${downloadedAmbients.size} custom tracks using $storageFormatted in internal filesDir",
                    fontSize = 14.sp,
                    color = GoldPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (downloadedAmbients.isEmpty()) {
                    Text(
                        text = "No custom atmosphere files stored.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(downloadedAmbients, key = { it.id }) { entity ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x1AFFFFFF))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entity.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "${String.format("%.1f MB", entity.fileSizeBytes / (1024f * 1024f))} • Internal Storage",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(onClick = {
                                    haptic.tap()
                                    onDeleteDownloaded(entity.id)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedSoundCard(
    entity: DownloadedAmbientEntity,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = rememberHapticController()

    GlassCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${String.format("%.1f MB", entity.fileSizeBytes / (1024f * 1024f))} • Infinite Loop (REPEAT_MODE_ALL)",
                    fontSize = 12.sp,
                    color = if (isPlaying) GoldPrimary else EmeraldMint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    haptic.tap()
                    onToggleFavorite()
                }) {
                    Icon(
                        imageVector = if (entity.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (entity.isFavorite) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isPlaying) GoldPrimary else Color(0x22FFFFFF))
                        .clickable {
                            haptic.tap()
                            onPlay()
                        }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause Loop" else "Start Infinite Loop",
                        tint = if (isPlaying) Color.Black else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = {
                    haptic.tap()
                    onDelete()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
