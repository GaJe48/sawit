package com.gaje48.lms.data

import android.net.Uri
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.coroutines.runSuspendCatching
import com.github.michaelbull.result.onErr
import com.github.michaelbull.result.onOk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.lms_rust.NotifCallback

class TransferRepository(
    private val internetDataSource: uniffi.lms_rust.InternetDataSource,
    private val storageDataSource: StorageDataSource,
) {
    suspend fun downloadFile(
        url: String,
        baseName: String,
        onProgress: (fileName: String, progress: Int) -> Unit,
    ): Result<String, Throwable> = coroutineBinding {
        withContext(Dispatchers.IO) {
            val tempFile = storageDataSource.createTempFile(baseName).bind()

            val notifCallback = object : NotifCallback {
                override fun onProgress(fileName: String, progress: Int) =
                    onProgress(fileName, progress)
            }

            val pfd = storageDataSource.openWriteHandle(tempFile.uri).bind()

            runSuspendCatching {
                val clonePfd = pfd.dup()

                internetDataSource
                    .downloadFile(
                        clonePfd.detachFd(),
                        url,
                        tempFile.uniqueBaseName,
                        notifCallback,
                    ).also { pfd.close() }
            }.onOk {
                storageDataSource.finalizeDownload(tempFile.uri, it)
            }.onErr {
                storageDataSource.deleteFile(tempFile.uri)
            }.bind()
        }
    }

    suspend fun uploadSubmission(
        uri: Uri,
        taskUrl: String,
        onProgress: (fileName: String, progress: Int) -> Unit,
    ): Result<String, Throwable> = coroutineBinding {
        val uploadHandler = storageDataSource.openFileForUpload(uri).bind()
        val fileSize = uploadHandler.fileSize
        val pfd = uploadHandler.pfd

        if (fileSize > 20 * 1024 * 1024) Err(TransferException("Ukuran berkas melebihi batas 20MB")).bind()

        val callback = object : NotifCallback {
            override fun onProgress(fileName: String, progress: Int) =
                onProgress(fileName, progress)
        }

        runSuspendCatching {
            val clonePfd = pfd.dup()

            internetDataSource
                .uploadSubmission(
                    taskUrl,
                    uploadHandler.fileName,
                    fileSize.toInt(),
                    clonePfd.detachFd(),
                    callback,
                ).also { pfd.close() }
        }.bind()
    }
}
