package com.gaje48.lms.data.db

import com.gaje48.lms.model.Course
import com.gaje48.lms.model.MeetingsByCourse
import com.gaje48.lms.model.AttendancesByCourse
import com.gaje48.lms.model.Content
import com.gaje48.lms.model.Student
import com.gaje48.lms.model.Assignment
import com.gaje48.lms.model.Meeting

fun Student.toEntity() = StudentEntity(
    npm = npm,
    studentName = studentName,
    studyProgram = studyProgram,
    classCode = classCode,
    studentProfilePictureUrl = studentProfilePictureUrl
)

fun Course.toEntity() = CourseEntity(
    courseCode = courseCode,
    courseName = courseName,
    day = day,
    clock = clock,
    room = room,
    lecturerName = lecturerName,
    lecturerPhoneNumber = lecturerPhoneNumber,
    lecturerProfilePictureUrl = lecturerProfilePictureUrl
)

fun MeetingsByCourse.toMeetingEntities() = meetings.map { (meetingNumber, meetingUrl) ->
    MeetingEntity(
        courseCode = courseCode,
        meetingNumber = meetingNumber,
        meetingUrl = meetingUrl
    )
}

fun AttendancesByCourse.toAttendanceEntities() = attendances.mapIndexed { index, isAttended ->
    AttendanceEntity(
        courseCode = courseCode,
        attendanceIndex = index,
        isAttended = isAttended
    )
}

fun Content.toEntity(foreignKey: String) = ContentEntity(
    meetingUrl = foreignKey,
    type = type,
    title = title,
    contentUrl = contentUrl
)

fun Assignment.toEntity(foreignKey: String) = AssignmentEntity(
    meetingUrl = foreignKey,
    assignmentUrl = assignmentUrl,
    description = description,
    assignmentFileUrl = assignmentFileUrl,
    submissionFileUrl = submissionFileUrl,
    deadline = deadline,
    isSubmitted = isSubmitted,
    isOverdue = isOverdue
)

fun StudentEntity.toDomain() = Student(
    npm = npm,
    studentName = studentName,
    studyProgram = studyProgram,
    classCode = classCode,
    studentProfilePictureUrl = studentProfilePictureUrl
)

fun CourseEntity.toDomain() = Course(
    courseCode = courseCode,
    courseName = courseName,
    day = day,
    clock = clock,
    room = room,
    lecturerName = lecturerName,
    lecturerPhoneNumber = lecturerPhoneNumber,
    lecturerProfilePictureUrl = lecturerProfilePictureUrl
)

fun MeetingEntity.toDomain() = Meeting(
    meetingNumber = meetingNumber,
    meetingUrl = meetingUrl
)

fun ContentEntity.toDomain() = Content(
    type = type,
    title = title,
    contentUrl = contentUrl
)