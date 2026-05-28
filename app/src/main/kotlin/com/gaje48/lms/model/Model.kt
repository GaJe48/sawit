package com.gaje48.lms.model

enum class UpdateAction {
    LOADING,
    REFRESH,
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
    val type: String,
    val title: String,
    val contentUrl: String,
    val meetingNumber: Byte,
    val courseName: String,
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
