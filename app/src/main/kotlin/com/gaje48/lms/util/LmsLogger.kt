package com.gaje48.lms.util

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object LmsLogger {
    fun writeLog(message: String) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.forLanguageTag("id-ID"))
        val timestamp = LocalDateTime.now().format(formatter)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val sawitDir = File(downloadDir, "sawit").apply { if (!exists()) mkdirs() }
                val logFile = File(sawitDir, "log.txt")

                FileWriter(logFile, true).use { writer -> writer.append("[$timestamp] $message\n") }
            } catch (e: Exception) {
                android.util.Log.e("LmsLogger", "Gagal menulis log ke file", e)
            }
        }
    }
}
