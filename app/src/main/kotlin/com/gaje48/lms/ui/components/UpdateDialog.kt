package com.gaje48.lms.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaje48.lms.model.UpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    downloadProgress: Int?,
    isDownloading: Boolean,
    onUpdateClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismissClick()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        },
        iconContentColor = MaterialTheme.colorScheme.primary,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Pembaruan Tersedia 🚀",
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Versi ${updateInfo.versionName} sudah dirilis",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                            shape = RoundedCornerShape(16.dp),
                        ).border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.08f),
                            shape = RoundedCornerShape(16.dp),
                        ).padding(12.dp),
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = "Catatan Rilis:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = updateInfo.releaseNotes.ifEmpty { "Peningkatan performa dan perbaikan bug." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp,
                        )
                    }
                }

                if (isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Mengunduh pembaruan...",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${downloadProgress ?: 0}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        val progressFraction = ((downloadProgress ?: 0).coerceIn(0, 100)) / 100f

                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(0.15f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            val confirmInteraction = remember { MutableInteractionSource() }
            val isConfirmPressed by rememberPressedState(confirmInteraction)
            val confirmScale by animateFloatAsState(
                targetValue = if (isConfirmPressed) 0.95f else 1f,
                label = "update_confirm_scale",
            )

            Button(
                onClick = {
                    scope.launch {
                        delay(150.milliseconds)
                        onUpdateClick()
                    }
                },
                enabled = !isDownloading,
                modifier = Modifier.graphicsLayer {
                    scaleX = confirmScale
                    scaleY = confirmScale
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                interactionSource = confirmInteraction,
            ) {
                Text(
                    text = if (isDownloading) "Mengunduh..." else "Perbarui Sekarang",
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            if (!isDownloading) {
                val dismissInteraction = remember { MutableInteractionSource() }
                val isDismissPressed by rememberPressedState(dismissInteraction)
                val dismissScale by animateFloatAsState(
                    targetValue = if (isDismissPressed) 0.95f else 1f,
                    label = "update_dismiss_scale",
                )

                Button(
                    onClick = {
                        scope.launch {
                            delay(150.milliseconds)
                            onDismissClick()
                        }
                    },
                    modifier = Modifier.graphicsLayer {
                        scaleX = dismissScale
                        scaleY = dismissScale
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(0.2f)),
                    interactionSource = dismissInteraction,
                ) {
                    Text(
                        text = "Nanti",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(0.95f),
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.2f),
            shape = RoundedCornerShape(24.dp),
        ),
    )
}
