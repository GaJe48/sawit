package com.gaje48.lms.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateInfo(
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("release_notes") val releaseNotes: String,
    @SerialName("apk_url") val apkUrl: String,
)
