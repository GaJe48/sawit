package com.gaje48.lms

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.arkivanov.decompose.defaultComponentContext
import com.gaje48.lms.navigation.DefaultRootComponent
import com.gaje48.lms.navigation.LmsApp
import com.gaje48.lms.services.LmsSyncScheduler
import com.gaje48.lms.services.LmsSyncService
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
    private val syncScheduler: LmsSyncScheduler = get()
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val rootComponent = DefaultRootComponent(componentContext = defaultComponentContext())

        rootComponent.checkLoginStatus()

        rootComponent.authState.subscribe { authState ->
            if (authState) {
                syncScheduler.scheduleSyncIfNecessary()
            } else {
                syncScheduler.cancelNextSync()
                stopService(Intent(applicationContext, LmsSyncService::class.java))
            }
        }

        setContent { LmsApp(rootComponent = rootComponent) }
    }
}
