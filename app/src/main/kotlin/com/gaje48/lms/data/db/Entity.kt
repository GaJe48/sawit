package com.gaje48.lms.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "student")
data class StudentEntity(
    @PrimaryKey val npm: String,
    val name: String,
    val studyProgram: String,
    val classCode: String,
    val profilePictureUrl: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "course")
data class CourseEntity(
    @PrimaryKey val courseCode: String,
    val courseName: String,
    val day: String,
    val time: String,
    val room: String,
    val lecturerName: String,
    val lecturerPhoneNumber: String,
    val lecturerProfilePictureUrl: String,
    val cachedAt: Long = System.currentTimeMillis()
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
    val meetingIndex: Int,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "meeting_content",
    foreignKeys = [ForeignKey(
        entity = MeetingEntity::class,
        parentColumns = ["meetingUrl"],
        childColumns = ["meetingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("meetingId")]
)
data class MeetingContentEntity(
    @PrimaryKey val contentUrl: String,
    val meetingId: String,
    val contentType: String,
    val title: String,
    val cachedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "assignment",
    foreignKeys = [ForeignKey(
        entity = MeetingEntity::class,
        parentColumns = ["meetingUrl"],
        childColumns = ["meetingId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("meetingId")]
)
data class AssignmentEntity(
    @PrimaryKey val assignmentUrl: String,
    val meetingId: String,
    val description: String?,
    val assignmentFileUrl: String?,
    val submissionFileUrl: String?,
    val dueDate: String,
    val isSubmitted: Boolean,
    val isOverdue: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
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
    val isAttended: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
)