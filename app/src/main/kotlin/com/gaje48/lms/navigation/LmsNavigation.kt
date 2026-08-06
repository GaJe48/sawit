package com.gaje48.lms.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.androidPredictiveBackAnimatableV2
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimator
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.gaje48.lms.R
import com.gaje48.lms.ui.components.SyncIndicator
import com.gaje48.lms.ui.components.UpdateDialog
import com.gaje48.lms.ui.screens.assignment.AssignmentScreen
import com.gaje48.lms.ui.screens.attendance.AttendanceScreen
import com.gaje48.lms.ui.screens.dashboard.DashboardScreen
import com.gaje48.lms.ui.screens.login.LoginScreen
import com.gaje48.lms.ui.screens.meeting.MeetingScreen
import com.gaje48.lms.ui.theme.LMSUnindraTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun LmsApp(rootComponent: RootComponent) {
    val context = LocalContext.current

    var hasNotif by remember { mutableStateOf(true) }
    var hasBatteryOpt by remember { mutableStateOf(true) }

    LifecycleResumeEffect(Unit) {
        hasNotif = isNotifGranted(context)
        hasBatteryOpt = isIgnoreBattery(context)

        onPauseOrDispose { }
    }

    LMSUnindraTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            LmsAppContent(rootComponent = rootComponent)

            if (!hasNotif || !hasBatteryOpt) {
                BlockerDialog(
                    onNotif = {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }.also {
                                context.startActivity(it)
                            }
                    },
                    onBatteryOpt = {
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .apply {
                                data = "package:${context.packageName}".toUri()
                            }.also {
                                context.startActivity(it)
                            }
                    },
                    hasNotif = hasNotif,
                    hasBatteryOpt = hasBatteryOpt,
                )
            }
        }
    }
}

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun LmsAppContent(rootComponent: RootComponent) {
    val authState by rootComponent.authState.subscribeAsState()
    val isRefreshing by rootComponent.isRefreshing.subscribeAsState()
    val updateState by rootComponent.updateState.subscribeAsState()

    val scope = rememberCoroutineScope()
    val pullToRefreshState = rememberPullToRefreshState()
    val snackbarHostState = remember { SnackbarHostState() }

    val showSnackbar: (String) -> Unit = { msg ->
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    ObserveSnackbarEvents(rootComponent.snackbarEvent, showSnackbar)

    updateState.updateInfo?.let { info ->
        UpdateDialog(
            updateInfo = info,
            downloadProgress = updateState.downloadProgress,
            isDownloading = updateState.isDownloading,
            onUpdateClick = { rootComponent.startUpdate(info.apkUrl) },
            onDismissClick = { rootComponent.dismissUpdate() },
        )
    }

    val content = @Composable {
        Children(
            stack = rootComponent.childStack,
            animation = predictiveBackAnimation(
                backHandler = rootComponent.backHandler,
                onBack = rootComponent::onBackClicked,
                fallbackAnimation = stackAnimation(
                    stackAnimator(
                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
                        frame = { factor, _, content ->
                            content(
                                Modifier.graphicsLayer {
                                    translationX = if (factor < 0f) {
                                        size.width * (factor / 3f)
                                    } else {
                                        size.width * factor
                                    }

                                    alpha = if (factor < 0f) {
                                        1f + (factor * 0.5f)
                                    } else {
                                        1f
                                    }
                                },
                            )
                        },
                    ),
                ),
                selector = { backEvent, _, _ -> androidPredictiveBackAnimatableV2(backEvent) },
            ),
        ) { child ->
            when (val instance = child.instance) {
                is Child.Login -> {
                    LoginScreen(instance.component)
                }

                is Child.Dashboard -> {
                    ObserveSnackbarEvents(instance.component.snackbarEvent, showSnackbar)
                    DashboardScreen(instance.component)
                }

                is Child.Meeting -> {
                    ObserveSnackbarEvents(instance.component.snackbarEvent, showSnackbar)
                    MeetingScreen(instance.component)
                }

                is Child.Assignment -> {
                    ObserveSnackbarEvents(instance.component.snackbarEvent, showSnackbar)
                    AssignmentScreen(instance.component)
                }

                is Child.Attendance -> {
                    ObserveSnackbarEvents(instance.component.snackbarEvent, showSnackbar)
                    AttendanceScreen(instance.component)
                }
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        state = pullToRefreshState,
        onRefresh = { rootComponent.refresh() },
        contentAlignment = Alignment.TopCenter,
        enabled = authState,
        indicator = {
            SyncIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        content()

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun BlockerDialog(
    onNotif: () -> Unit,
    onBatteryOpt: () -> Unit,
    hasNotif: Boolean,
    hasBatteryOpt: Boolean,
) {
    AlertDialog(
        onDismissRequest = { },
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(R.string.blocker_title),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.blocker_title),
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
                    text = stringResource(R.string.blocker_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (!hasNotif) {
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
                                    text = stringResource(R.string.notif_permission_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.notif_permission_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onNotif,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(R.string.notif_permission_button_settings),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                if (!hasBatteryOpt) {
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
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.battery_permission_title),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.battery_permission_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onBatteryOpt,
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.battery_permission_button_disable), fontWeight = FontWeight.Bold)
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

@Composable
private fun ObserveSnackbarEvents(
    flow: Flow<String>,
    showSnackbar: (String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(flow, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { showSnackbar(it) }
        }
    }
}

private fun isNotifGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

    return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

private fun isIgnoreBattery(context: Context) =
    context
        .getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
