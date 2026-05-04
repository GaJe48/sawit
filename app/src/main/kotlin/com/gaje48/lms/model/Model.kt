package com.gaje48.lms.model

import java.io.InputStream

sealed interface StatusPresensi {
    data object SudahHadir : StatusPresensi
    data class BelumHadirAdaLink(val linkDownload: String) : StatusPresensi
    data object BelumHadirTanpaLink : StatusPresensi
}

enum class LoadMode {
    LOADING,
    REFRESH,
}

data class DashboardData(
    val student: Student,
    val courses: List<Course>,
    val allPresences: List<AttendancesByCourse>,
    val allMeetings: List<MeetingsByCourse>
)

data class Student(
    val npm: String,
    val studentName: String,
    val studyProgram: String,
    val classCode: String,
    val studentProfilePictureUrl: String
)

data class Course(
    val courseCode: String,
    val courseName: String,
    val day: String,
    val clock: String,
    val room: String,
    val lecturerName: String,
    val lecturerPhoneNumber: String,
    val lecturerProfilePictureUrl: String
)

data class AttendancesByCourse(
    val courseCode: String,
    val attendances: List<Boolean>
)

data class MeetingsByCourse(
    val courseCode: String,
    val meetings: List<Meeting>
)

data class Meeting(
    val meetingNumber: Int,
    val meetingUrl: String
)

data class Content(val type: String, val title: String, val contentUrl: String)

data class Assignment(
    val meetingNumber: Int,
    val assignmentUrl: String,
    val description: String?,
    val assignmentFileUrl: String?,
    val deadline: String,
    val submissionFileUrl: String?,
    val isSubmitted: Boolean,
    val isOverdue: Boolean
)

data class FileSource(val name: String, val size: Long, val stream: InputStream)

class SessionExpiredException : Exception("Session has expired. Please login again.")

class AccountProblemException : Exception("There is a problem with your account.")
