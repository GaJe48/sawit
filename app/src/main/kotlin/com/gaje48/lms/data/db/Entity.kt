package com.gaje48.lms.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "student")
data class StudentEntity(
    @PrimaryKey val npm: String,
    val studentName: String,
    val studyProgram: String,
    val classCode: String,
    val studentProfilePictureUrl: String
)

@Entity(tableName = "course")
data class CourseEntity(
    @PrimaryKey val courseCode: String,
    val courseName: String,
    val day: String,
    val clock: String,
    val room: String,
    val lecturerName: String,
    val lecturerPhoneNumber: String,
    val lecturerProfilePictureUrl: String
)

@Entity(
    tableName = "meeting",
    foreignKeys = [ForeignKey(
        entity = CourseEntity::class,
        parentColumns = ["courseCode"],
        childColumns = ["courseCode"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("courseCode")]
)
data class MeetingEntity(
    @PrimaryKey val meetingUrl: String,
    val courseCode: String,
    val meetingNumber: Int
)

@Entity(
    tableName = "content",
    primaryKeys = ["meetingUrl", "title"],
    foreignKeys = [ForeignKey(
        entity = MeetingEntity::class,
        parentColumns = ["meetingUrl"],
        childColumns = ["meetingUrl"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("meetingUrl")]
)
data class ContentEntity(
    val meetingUrl: String,
    val type: String,
    val title: String,
    val contentUrl: String
)

@Entity(
    tableName = "assignment",
    foreignKeys = [ForeignKey(
        entity = MeetingEntity::class,
        parentColumns = ["meetingUrl"],
        childColumns = ["meetingUrl"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("meetingUrl")]
)
data class AssignmentEntity(
    @PrimaryKey val assignmentUrl: String,
    val meetingUrl: String,
    val description: String?,
    val assignmentFileUrl: String?,
    val submissionFileUrl: String?,
    val deadline: String,
    val isSubmitted: Boolean,
    val isOverdue: Boolean
)

@Entity(
    tableName = "attendance",
    primaryKeys = ["courseCode", "attendanceIndex"],
    foreignKeys = [ForeignKey(
        entity = CourseEntity::class,
        parentColumns = ["courseCode"],
        childColumns = ["courseCode"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("courseCode")]
)
data class AttendanceEntity(
    val courseCode: String,
    val attendanceIndex: Int,
    val isAttended: Boolean
)