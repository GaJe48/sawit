package com.gaje48.lms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.gaje48.lms.navigation.LmsApp
import com.gaje48.lms.ui.MainViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        super.onCreate(savedInstanceState)
        viewModel.checkLoginStatus()
        splashScreen.setKeepOnScreenCondition { !viewModel.isSplashReady.value }

        setContent { LmsApp(mainViewModel = viewModel) }
    }
}
