package com.gaje48.lms.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.runCatching
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TransferException(msg: String = "Terjadi kesalahan pada media penyimpanan") : Exception(msg)

data class TempFile(
    val uri: Uri,
    val uniqueBaseName: String,
)

data class UploadHandler(
    val pfd: ParcelFileDescriptor,
    val fileName: String,
    val fileSize: Long,
)

class StorageDataSource(context: Context) {
    private val resolver = context.contentResolver
    private val mutex = Mutex()

    suspend fun createTempFile(baseName: String): Result<TempFile, Throwable> = coroutineBinding {
        mutex.withLock {
            val uniqueBaseName = getUniqueBaseName(baseName).bind()

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, uniqueBaseName)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/sawit")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: Err(TransferException("Gagal membuat entri file di penyimpanan MediaStore")).bind()

            TempFile(uri, uniqueBaseName)
        }
    }

    fun openWriteHandle(uri: Uri): Result<ParcelFileDescriptor, Throwable> = binding {
        runCatching { resolver.openFileDescriptor(uri, "w") }.bind()
            ?: run {
                deleteFile(uri)

                Err(TransferException("Gagal membuka file deskriptor untuk penulisan file")).bind()
            }
    }

    fun finalizeDownload(uri: Uri, finalFileName: String) {
        val updateValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, finalFileName)
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        resolver.update(uri, updateValues, null, null)
    }

    fun deleteFile(uri: Uri) {
        resolver.delete(uri, null, null)
    }

    private fun getUniqueBaseName(baseName: String): Result<String, Throwable> = binding {
        val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf("$baseName%", "${Environment.DIRECTORY_DOWNLOADS}/sawit/")

        val existingNames = mutableSetOf<String>()

        val isAtLeastA11 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        val uri =
            if (isAtLeastA11) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                @Suppress("DEPRECATION")
                MediaStore.setIncludePending(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }

        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)

            if (isAtLeastA11) {
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
        }

        val cursor = resolver.query(uri, projection, queryArgs, null)
            ?: Err(TransferException("Gagal memeriksa nama file di penyimpanan")).bind()

        runCatching {
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val displayName = cursor.getString(nameIndex)

                val nameWithoutExt = displayName.substringBeforeLast(".")
                existingNames.add(nameWithoutExt)
            }

            cursor.close()
        }.bind()

        var counter = 0
        var uniqueName = baseName

        while (existingNames.contains(uniqueName)) {
            counter++
            uniqueName = "$baseName ($counter)"
        }

        uniqueName
    }

    fun openFileForUpload(uri: Uri): Result<UploadHandler, Throwable> = binding {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)

        val cursor = resolver.query(uri, projection, null, null)
            ?: Err(TransferException("Gagal membaca metadata file upload")).bind()

        if (!cursor.moveToFirst()) Err(TransferException("Metadata file upload tidak ditemukan")).bind()

        runCatching {
            val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)

            val fileName = cursor.getString(nameIndex)
            val fileSize = cursor.getLong(sizeIndex)

            cursor.close()

            val pfd = resolver.openFileDescriptor(uri, "r")
                ?: Err(TransferException("Gagal membuka file deskriptor untuk dibaca")).bind()

            UploadHandler(pfd, fileName, fileSize)
        }.bind()
    }
}
