package com.gaje48.lms.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gaje48.lms.model.UpdateInfo

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    downloadProgress: Int?,
    isDownloading: Boolean,
    onUpdateClick: () -> Unit,
    onDismissClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismissClick()
            }
        },
        shape = RoundedCornerShape(24.dp),
        title = {
            Column {
                Text(
                    text = "Pembaruan Tersedia 🚀",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Versi terbaru ${updateInfo.versionName} sudah dirilis.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Catatan Rilis:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = updateInfo.releaseNotes.ifEmpty { "Peningkatan performa dan perbaikan bug." },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp,
                )

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (downloadProgress != null) {
                            "Mengunduh pembaruan... $downloadProgress%"
                        } else {
                            "Mengunduh pembaruan..."
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { downloadProgress.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdateClick,
                enabled = !isDownloading,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (isDownloading) {
                        "Mengunduh..."
                    } else {
                        "Perbarui Sekarang"
                    },
                )
            }
        },
        dismissButton = {
            if (!isDownloading) {
                OutlinedButton(
                    onClick = onDismissClick,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Nanti")
                }
            }
        },
    )
}
