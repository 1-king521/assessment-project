package com.acme.assessment.service

import com.acme.assessment.entity.AssessmentAssignment
import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.OperationLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReviewDeadlineReminderServiceTest {
    private val assignmentRepository = mock<AssessmentAssignmentRepository>()
    private val operationLogRepository = mock<OperationLogRepository>()
    private val notificationEventListener = mock<NotificationEventListener>()
    private val now = Instant.parse("2026-09-20T08:00:00Z")
    private val service = ReviewDeadlineReminderService(
        assignmentRepository,
        operationLogRepository,
        notificationEventListener,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `marks reminder after notification succeeds`() {
        val assignment = AssessmentAssignment(
            id = 1,
            taskId = 2,
            reviewerUserId = 3,
            status = AssignmentStatus.PENDING,
            reviewDueAt = now.minusSeconds(1),
        )
        whenever(assignmentRepository.findOverdueForReminder(any(), any())).thenReturn(listOf(assignment))
        whenever(notificationEventListener.sendReviewOverdue(any())).thenReturn(true)

        service.remindOverdueAssignments()

        assertThat(assignment.overdueReminderSentAt).isEqualTo(now)
        verify(operationLogRepository).save(any<OperationLog>())
    }

    @Test
    fun `keeps reminder pending when notification fails`() {
        val assignment = AssessmentAssignment(
            id = 1,
            taskId = 2,
            reviewerUserId = 3,
            status = AssignmentStatus.IN_PROGRESS,
            reviewDueAt = now.minusSeconds(1),
        )
        whenever(assignmentRepository.findOverdueForReminder(any(), any())).thenReturn(listOf(assignment))
        whenever(notificationEventListener.sendReviewOverdue(any())).thenReturn(false)

        service.remindOverdueAssignments()

        assertThat(assignment.overdueReminderSentAt).isNull()
    }
}
