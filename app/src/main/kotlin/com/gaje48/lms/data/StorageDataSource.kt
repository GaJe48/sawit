package com.gaje48.lms.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageDataSource(
    context: Context,
) {
    private val resolver = context.contentResolver

    suspend fun createDownloadFd(fileName: String) =
        withContext(Dispatchers.IO) {
            val contentValues =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/elemes")
                }

            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw uniffi.lms_rust.LmsException.StorageException("Gagal membuat file download")

            resolver.openFileDescriptor(uri, "w")?.detachFd()
                ?: throw uniffi.lms_rust.LmsException.StorageException("Gagal membuka file download")
        }

    suspend fun openFileForUpload(uri: Uri) =
        withContext(Dispatchers.IO) {
            val cursor =
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                ) ?: error("Gagal mendapatkan informasi file")

            val (fileName, fileSize) =
                cursor.use { c ->
                    if (!c.moveToFirst()) error("Gagal membaca metadata file")
                    c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) to
                        c.getLong(c.getColumnIndexOrThrow(OpenableColumns.SIZE))
                }

            val pfd = resolver.openFileDescriptor(uri, "r") ?: error("File tidak valid")
            Triple(fileName, fileSize, pfd.detachFd())
        }
}
