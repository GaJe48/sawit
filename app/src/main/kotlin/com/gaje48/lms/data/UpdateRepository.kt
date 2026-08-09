package com.gaje48.lms.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.gaje48.lms.BuildConfig
import com.gaje48.lms.model.UpdateInfo
import com.gaje48.lms.services.InstallStatusReceiver
import com.gaje48.lms.services.LmsUpdateService
import com.gaje48.lms.services.LmsUpdateService.Companion.EXTRA_APK_URL
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.runCatching
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import uniffi.lms_rust.InternetDataSource
import uniffi.lms_rust.NotifCallback
import java.io.File

class UpdateRepository(
    private val context: Context,
    private val internetDataSource: InternetDataSource,
) {
    private companion object {
        const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/GaJe48/sawit/refs/heads/master/update.json"
    }

    private val json = Json
    lateinit var activeDeferred: CompletableDeferred<Result<File, Throwable>>

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress = _downloadProgress.asStateFlow()

    suspend fun getLatestApk(apkUrl: String): Result<File, Throwable> {
        _downloadProgress.value = 0

        activeDeferred = CompletableDeferred()

        val intent = Intent(context, LmsUpdateService::class.java).apply {
            putExtra(EXTRA_APK_URL, apkUrl)
        }
        context.startForegroundService(intent)

        return activeDeferred.await()
    }

    suspend fun checkForUpdate(): Result<UpdateInfo?, Throwable> = coroutineBinding {
        val updateInfo = runSuspendCatching {
            val responseText = internetDataSource.fetchText(UPDATE_JSON_URL)

            json.decodeFromString<UpdateInfo>(responseText)
        }.bind()

        if (updateInfo.versionCode > BuildConfig.VERSION_CODE) {
            updateInfo
        } else {
            null
        }
    }

    suspend fun downloadApk(
        apkUrl: String,
        onProgress: (Int) -> Unit,
    ): Result<File, Throwable> = coroutineBinding {
        val apkFile = File(context.cacheDir, apkUrl.hashCode().toString())

        val callback = object : NotifCallback {
            override fun onProgress(fileName: String, progress: Int) {
                _downloadProgress.value = progress

                onProgress(progress)
            }
        }

        withContext(Dispatchers.IO) {
            runSuspendCatching {
                internetDataSource.downloadFileToPath(apkFile.path, apkUrl, callback)
            }.bind()
        }

        apkFile
    }

    fun installApk(apkFile: File): Result<Unit, Throwable> = runCatching {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return@runCatching
        }

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        session.openWrite("sawit", 0, apkFile.length()).use { outputStream ->
            apkFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
            session.fsync(outputStream)
        }

        val receiverIntent = Intent(context, InstallStatusReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, sessionId, receiverIntent, flags)

        session.commit(pendingIntent.intentSender)
        session.close()
    }
}
