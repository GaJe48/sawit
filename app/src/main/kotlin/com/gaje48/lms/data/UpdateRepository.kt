package com.gaje48.lms.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.gaje48.lms.BuildConfig
import com.gaje48.lms.model.UpdateInfo
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.runCatching
import kotlinx.coroutines.Dispatchers
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
        const val UPDATE_JSON_URL = "https://raw.githubusercontent.com/GaJe48/sawit/main/update.json"
    }

    suspend fun checkForUpdate(): Result<UpdateInfo?, Throwable> = coroutineBinding {
        val updateInfo = runSuspendCatching {
            val responseText = internetDataSource.fetchText(UPDATE_JSON_URL)

            Json.decodeFromString<UpdateInfo>(responseText)
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
        val callback = object : NotifCallback {
            override fun onProgress(fileName: String, progress: Int) = onProgress(progress)
        }

        val apkFile = File(context.cacheDir, "sawit.apk")

        withContext(Dispatchers.IO) {
            runSuspendCatching {
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                internetDataSource.downloadFileToPath(apkFile.absolutePath, apkUrl, callback)
            }.bind()
        }

        apkFile
    }

    fun installApk(apkFile: File): Result<Unit, Throwable> = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    suspend fun downloadAndInstallApk(
        apkUrl: String,
        onProgress: (Int) -> Unit,
    ): Result<Unit, Throwable> = coroutineBinding {
        val apkFile = downloadApk(apkUrl, onProgress).bind()

        installApk(apkFile).bind()
    }
}
