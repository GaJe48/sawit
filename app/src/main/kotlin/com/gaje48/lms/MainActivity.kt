package com.gaje48.lms

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gaje48.lms.navigation.LmsApp
import com.gaje48.lms.services.LmsWatchService
import com.gaje48.lms.ui.MainViewModel
import com.gaje48.lms.ui.theme.LMSUnindraTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()

    private val showPermissionBlocker = mutableStateOf(false)
    private val isNotificationMissingState = mutableStateOf(false)
    private val isExactAlarmMissingState = mutableStateOf(false)
    private val shouldRedirectNotification = mutableStateOf(false)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            checkPermissionsAndStartService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        viewModel.checkLoginStatus()
        splashScreen.setKeepOnScreenCondition { !viewModel.isSplashReady.value }

        setContent {
            LMSUnindraTheme {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (showPermissionBlocker.value) {
                        PermissionBlockerScreen(
                            onRequestNotification = {
                                if (shouldRedirectNotification.value) {
                                    val intent =
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                        }
                                    startActivity(intent)
                                } else {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            },
                            onRequestExactAlarm = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val intent =
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.fromParts("package", packageName, null)
                                        }
                                    startActivity(intent)
                                }
                            },
                            missingNotification = isNotificationMissingState.value,
                            missingExactAlarm = isExactAlarmMissingState.value,
                            redirectNotification = shouldRedirectNotification.value,
                        )
                    } else {
                        LmsApp(mainViewModel = viewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndStartService()
    }

    private fun checkPermissionsAndStartService() {
        val missingNotification = isNotificationPermissionMissing()
        val missingExactAlarm = isExactAlarmPermissionMissing()
        isNotificationMissingState.value = missingNotification
        isExactAlarmMissingState.value = missingExactAlarm
        shouldRedirectNotification.value = shouldRedirectNotificationToSettings()
        showPermissionBlocker.value = missingNotification || missingExactAlarm

        if (!showPermissionBlocker.value) {
            val serviceIntent = Intent(this, LmsWatchService::class.java)
            startForegroundService(serviceIntent)
        }
    }

    private fun shouldRedirectNotificationToSettings(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val missing = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            return missing && !shouldShowRationale
        }
        return false
    }

    private fun isNotificationPermissionMissing(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        }
        return false
    }

    private fun isExactAlarmPermissionMissing(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }
        return false
    }
}

@Composable
fun PermissionBlockerScreen(
    onRequestNotification: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    missingNotification: Boolean,
    missingExactAlarm: Boolean,
    redirectNotification: Boolean,
) {
    AlertDialog(
        onDismissRequest = { },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Peringatan",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = "Izin Diperlukan",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Aplikasi memerlukan izin berikut agar pemantauan tugas kuliah dapat berjalan secara andal di background. Aplikasi tidak dapat digunakan sampai izin diberikan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (missingNotification) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Izin Notifikasi",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Diperlukan untuk memberi tahu Anda secara instan jika ada tugas baru.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRequestNotification,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = if (redirectNotification) "Buka Pengaturan Notifikasi" else "Aktifkan Notifikasi",
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                if (missingExactAlarm) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            ),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Izin Alarm Presisi",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Diperlukan agar pengecekan tugas berjalan tepat waktu setiap 15 menit sekali.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRequestExactAlarm,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Buka Pengaturan Alarm", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { },
        modifier =
            Modifier
                .border(
                    width = 1.dp,
                    brush =
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                            ),
                        ),
                    shape = RoundedCornerShape(24.dp),
                ).clip(RoundedCornerShape(24.dp)),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    )
}
