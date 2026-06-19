package com.gaje48.lms.util

import android.content.Context
import androidx.core.content.edit

class LmsSyncPrefs(
    context: Context,
) {
    companion object {
        private const val PREFS_NAME = "lms_sync_service_prefs"
        private const val KEY_NEXT_EXPECTED_SYNC_TIME = "lms_next_expected_sync_time"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var nextExpectedSyncTime: Long
        get() = prefs.getLong(KEY_NEXT_EXPECTED_SYNC_TIME, 0L)
        set(value) = prefs.edit { putLong(KEY_NEXT_EXPECTED_SYNC_TIME, value) }
}
