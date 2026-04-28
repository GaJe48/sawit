package com.gaje48.lms.data.db

import com.gaje48.lms.model.CourseInfo
import com.gaje48.lms.model.MeetingContent
import com.gaje48.lms.model.StudentInfo
import com.gaje48.lms.model.TaskInfo

fun StudentInfo.toEntity() = StudentEntity(
    npm = npm,
    name = studentName,
    studyProgram = studyProgram,
    classCode = classCode,
    profilePictureUrl = studentPhoto
)

fun CourseInfo.toEntity() = CourseEntity(
    courseCode = courseCode,
    courseName = courseName,
    day = day,
    time = clock,
    room = room,
    lecturerName = lecturerName,
    lecturerPhoneNumber = lecturerHp,
    lecturerProfilePictureUrl = lecturerPhoto
)

fun CourseInfo.toMeetingEntities() = allMeeting.map { (meetingIndex, meetingUrl) ->
    MeetingEntity(
        courseCode = courseCode,
        meetingIndex = meetingIndex,
        meetingUrl = meetingUrl
    )
}

fun Map<String, List<Boolean>>.toAttendanceEntities() = flatMap { (courseCode, presences) ->
    presences.mapIndexed { index, isAttended ->
        AttendanceEntity(
            courseCode = courseCode,
            attendanceIndex = index,
            isAttended = isAttended
        )
    }
}

fun MeetingContent.toEntity(meetingUrl: String) = MeetingContentEntity(
    meetingId = meetingUrl,
    contentType = type,
    title = desc,
    contentUrl = url
)

fun TaskInfo.toEntity(meetingUrl: String) = AssignmentEntity(
    meetingId = meetingUrl,
    assignmentUrl = taskUrl,
    description = message,
    assignmentFileUrl = taskFile,
    submissionFileUrl = viewUrl,
    dueDate = deadline,
    isSubmitted = isSubmitted,
    isOverdue = isExpired
)