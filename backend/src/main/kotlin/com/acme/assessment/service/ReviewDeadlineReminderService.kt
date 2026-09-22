package com.acme.assessment.service

import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.OperationLogRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Component
class ReviewDeadlineReminderService(
    private val assignmentRepository: AssessmentAssignmentRepository,
    private val operationLogRepository: OperationLogRepository,
    private val notificationEventListener: NotificationEventListener,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${app.review-reminder.fixed-delay-ms:60000}",
        initialDelayString = "\${app.review-reminder.initial-delay-ms:15000}",
    )
    @Transactional
    fun remindOverdueAssignments() {
        val now = clock.instant()
        val overdueAssignments = assignmentRepository.findOverdueForReminder(
            now,
            setOf(AssignmentStatus.PENDING, AssignmentStatus.IN_PROGRESS),
        )
        var remindersSent = 0
        overdueAssignments.forEach { assignment ->
            val dueAt = assignment.reviewDueAt ?: return@forEach
            val event = ReviewOverdueEvent(
                taskId = assignment.taskId,
                reviewerUserId = assignment.reviewerUserId,
                assignmentId = requireNotNull(assignment.id),
                reviewDueAt = dueAt,
            )
            if (!notificationEventListener.sendReviewOverdue(event)) return@forEach
            assignment.overdueReminderSentAt = now
            assignment.updatedAt = now
            remindersSent++
            operationLogRepository.save(
                OperationLog(
                    taskId = assignment.taskId,
                    operatorType = "SYSTEM",
                    action = "REVIEW_OVERDUE_REMINDER_SENT",
                    detailJson = "{\"assignmentId\":${assignment.id},\"reviewDueAt\":\"$dueAt\"}",
                    createdAt = now,
                ),
            )
        }
        if (remindersSent > 0) {
            logger.info("Sent overdue review reminders for {} assignments", remindersSent)
        }
    }
}
