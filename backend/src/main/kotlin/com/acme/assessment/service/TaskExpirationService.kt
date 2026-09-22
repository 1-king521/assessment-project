package com.acme.assessment.service

import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.entity.ReviewConclusion
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.OperationLogRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class TaskExpirationScheduler(
    private val taskRepository: AssessmentTaskRepository,
    private val processor: TaskExpirationProcessor,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${app.task-expiration.fixed-delay-ms:60000}",
        initialDelayString = "\${app.task-expiration.initial-delay-ms:10000}",
    )
    fun archiveOverdueTasks() {
        val now = clock.instant()
        taskRepository.findOverdueUnsubmittedIds(now, EXPIRABLE_STATUSES).forEach { taskId ->
            try {
                processor.archiveIfOverdue(taskId, now)
            } catch (exception: Exception) {
                logger.error("Failed to auto-archive overdue assessment task {}", taskId, exception)
            }
        }
    }

    private companion object {
        val EXPIRABLE_STATUSES = setOf(
            TaskStatus.DRAFT,
            TaskStatus.SENT,
            TaskStatus.OPENED,
            TaskStatus.IN_PROGRESS,
            TaskStatus.EXPIRED,
        )
    }
}

@Service
class TaskExpirationProcessor(
    private val taskRepository: AssessmentTaskRepository,
    private val assignmentRepository: AssessmentAssignmentRepository,
    private val operationLogRepository: OperationLogRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun archiveIfOverdue(taskId: Long, now: Instant): Boolean {
        val task = taskRepository.findByIdForUpdate(taskId) ?: return false
        if (
            task.status !in EXPIRABLE_STATUSES ||
            task.submittedAt != null ||
            task.deadline.isAfter(now)
        ) {
            return false
        }

        val oldStatus = task.status
        assignmentRepository.findAllByTaskId(taskId)
            .filter { it.status == AssignmentStatus.WAITING_CANDIDATE }
            .forEach {
                it.status = AssignmentStatus.CANCELLED
                it.cancelledAt = now
                it.updatedAt = now
            }

        task.status = TaskStatus.ARCHIVED
        task.archivedAt = now
        task.finalConclusion = ReviewConclusion.ABANDONED
        task.finalConclusionAt = now
        task.finalConclusionReason = ABANDONED_REASON
        task.updatedAt = now

        operationLogRepository.save(
            OperationLog(
                taskId = taskId,
                operatorType = "SYSTEM",
                action = "TASK_AUTO_ARCHIVED",
                fromStatus = oldStatus.name,
                toStatus = TaskStatus.ARCHIVED.name,
                detailJson = objectMapper.writeValueAsString(
                    mapOf(
                        "conclusion" to ReviewConclusion.ABANDONED.name,
                        "reason" to ABANDONED_REASON,
                        "deadline" to task.deadline.toString(),
                    ),
                ),
                createdAt = now,
            ),
        )
        return true
    }

    private companion object {
        const val ABANDONED_REASON = "截止时间内未提交测评"
        val EXPIRABLE_STATUSES = setOf(
            TaskStatus.DRAFT,
            TaskStatus.SENT,
            TaskStatus.OPENED,
            TaskStatus.IN_PROGRESS,
            TaskStatus.EXPIRED,
        )
    }
}
