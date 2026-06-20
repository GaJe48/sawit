package com.gaje48.lms

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gaje48.lms.navigation.LmsApp
import com.gaje48.lms.services.LmsSyncScheduler
import com.gaje48.lms.services.LmsSyncService
import com.gaje48.lms.ui.MainViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val viewModel: MainViewModel by viewModel()

        super.onCreate(savedInstanceState)
        viewModel.checkLoginStatus()
        splashScreen.setKeepOnScreenCondition { !viewModel.isSplashReady.value }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoggedIn.collect { isLoggedIn ->
                    val syncScheduler = LmsSyncScheduler(applicationContext)

                    if (isLoggedIn) {
                        syncScheduler.scheduleSyncIfNecessary()
                    } else {
                        syncScheduler.cancelNextSync()

                        stopService(Intent(applicationContext, LmsSyncService::class.java))
                    }
                }
            }
        }

        setContent { LmsApp(mainViewModel = viewModel) }
    }
}
