package com.example.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioTrack

@Composable
fun DeletePermanentlyConfirmDialog(
    track: AudioTrack,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = rememberHapticController()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_delete_permanently"),
        icon = {
            Icon(
                imageVector = Icons.Outlined.DeleteForever,
                contentDescription = null,
                tint = Color(0xFFEF5350),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Delete permanently?",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "This song will be permanently removed from your device storage:",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "• ${track.title}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subText = if (track.reciter.isNotBlank() && !track.reciter.equals("Unknown Artist", ignoreCase = true)) {
                    track.reciter
                } else {
                    "<unknown>"
                }
                Text(
                    text = "  $subText",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "This action cannot be undone.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEF5350)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    haptic.confirm()
                    onConfirmDelete()
                },
                modifier = Modifier.testTag("btn_confirm_permanent_delete")
            ) {
                Text(
                    text = "Delete",
                    color = Color(0xFFEF5350),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    haptic.tap()
                    onDismiss()
                },
                modifier = Modifier.testTag("btn_cancel_permanent_delete")
            ) {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp
                )
            }
        }
    )
}
