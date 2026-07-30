package com.gaje48.lms

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gaje48.lms.navigation.LmsApp
import com.gaje48.lms.services.LmsSyncScheduler
import com.gaje48.lms.services.LmsSyncService
import com.gaje48.lms.ui.MainViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()
    private val syncScheduler: LmsSyncScheduler by inject()
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        viewModel.checkLoginStatus()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLoggedIn.collect { isLoggedIn ->
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
