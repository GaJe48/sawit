package com.gaje48.lms.model

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

enum class AuthStatus {
    IDLE,
    LOADING,
    SUCCESS,
}

data class Student(
    val npm: String,
    val studentName: String,
    val studyProgram: String,
    val classCode: String?,
    val studentProfilePictureUrl: String?,
)

data class Course(
    val courseCode: String,
    val courseName: String,
    val day: String,
    val clock: String,
    val room: String,
    val lecturerName: String,
    val lecturerPhoneNumber: String?,
    val lecturerProfilePictureUrl: String?,
)

data class AttendancesByCourse(
    val courseCode: String,
    val attendances: List<Boolean>,
)

data class AttendanceScreenData(
    val isAttended: Boolean,
    val contentUrls: List<String>,
)

data class AttendanceVmData(
    val meetingNumber: Byte,
    val contentUrl: String,
)

data class Meeting(
    val meetingNumber: Byte,
    val meetingUrl: String,
)

data class Content(
    val type: String,
    val title: String,
    val contentUrl: String,
)

data class ContentVmData(
    val courseCode: String,
    val courseName: String,
    val meetingNumber: Byte,
    val type: String,
    val title: String,
    val contentUrl: String,
)

data class AssignmentScreenData(
    val assignmentUrl: String,
    val meetingUrl: String,
    val meetingNumber: Byte,
    val description: String?,
    val assignmentFileUrl: String?,
    val deadline: String,
    val submissionFileUrl: String?,
    val isSubmitted: Boolean,
    val isOverdue: Boolean,
)

data class AssignmentNotificationDetail(
    val courseName: String,
    val description: String?,
    val deadline: String,
)

@Serializable
object LoginNavKey : NavKey

@Serializable
object DashboardNavKey : NavKey

@Serializable
data class MeetingNavKey(
    val courseCode: String,
) : NavKey

@Serializable
data class AssignmentNavKey(
    val courseCode: String,
) : NavKey

@Serializable
data class AttendanceNavKey(
    val courseCode: String,
) : NavKey
