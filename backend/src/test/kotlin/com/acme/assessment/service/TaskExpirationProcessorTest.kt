package com.acme.assessment.service

import com.acme.assessment.entity.AssessmentAssignment
import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.AbandonmentSource
import com.acme.assessment.entity.OperationLog
import com.acme.assessment.entity.ReviewConclusion
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.OperationLogRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class TaskExpirationProcessorTest {
    private val taskRepository = mock<AssessmentTaskRepository>()
    private val assignmentRepository = mock<AssessmentAssignmentRepository>()
    private val operationLogRepository = mock<OperationLogRepository>()
    private val now = Instant.parse("2026-08-30T04:00:00Z")
    private lateinit var processor: TaskExpirationProcessor

    @BeforeEach
    fun setUp() {
        processor = TaskExpirationProcessor(
            taskRepository,
            assignmentRepository,
            operationLogRepository,
            jacksonObjectMapper(),
        )
    }

    @Test
    fun `archives an unsubmitted task when its deadline is reached`() {
        val task = AssessmentTask(
            id = 10,
            taskNo = "TEST-10",
            deadline = now,
            status = TaskStatus.IN_PROGRESS,
        )
        val assignment = AssessmentAssignment(
            id = 20,
            taskId = 10,
            status = AssignmentStatus.WAITING_CANDIDATE,
        )
        whenever(taskRepository.findByIdForUpdate(10)).thenReturn(task)
        whenever(assignmentRepository.findAllByTaskId(10)).thenReturn(listOf(assignment))

        assertThat(processor.archiveIfOverdue(10, now)).isTrue()

        assertThat(task.status).isEqualTo(TaskStatus.ARCHIVED)
        assertThat(task.archivedAt).isEqualTo(now)
        assertThat(task.finalConclusion).isEqualTo(ReviewConclusion.ABANDONED)
        assertThat(task.finalConclusionAt).isEqualTo(now)
        assertThat(task.finalConclusionReason).isEqualTo("截止时间内未提交测评")
        assertThat(task.abandonmentSource).isEqualTo(AbandonmentSource.TIMEOUT)
        assertThat(assignment.status).isEqualTo(AssignmentStatus.CANCELLED)
        assertThat(assignment.cancelledAt).isEqualTo(now)

        val log = argumentCaptor<OperationLog>()
        verify(operationLogRepository).save(log.capture())
        assertThat(log.firstValue.operatorType).isEqualTo("SYSTEM")
        assertThat(log.firstValue.action).isEqualTo("TASK_AUTO_ARCHIVED")
        assertThat(log.firstValue.fromStatus).isEqualTo(TaskStatus.IN_PROGRESS.name)
        assertThat(log.firstValue.toStatus).isEqualTo(TaskStatus.ARCHIVED.name)
        assertThat(log.firstValue.detailJson).contains(ReviewConclusion.ABANDONED.name)
    }

    @Test
    fun `does not archive a task that was submitted before processing`() {
        val task = AssessmentTask(
            id = 10,
            deadline = now.minusSeconds(1),
            submittedAt = now.minusSeconds(2),
            status = TaskStatus.SUBMITTED,
        )
        whenever(taskRepository.findByIdForUpdate(10)).thenReturn(task)

        assertThat(processor.archiveIfOverdue(10, now)).isFalse()

        assertThat(task.status).isEqualTo(TaskStatus.SUBMITTED)
        verify(assignmentRepository, never()).findAllByTaskId(any())
        verify(operationLogRepository, never()).save(any())
    }

    @Test
    fun `archives an unsubmitted legacy draft task when its deadline is reached`() {
        val task = AssessmentTask(
            id = 10,
            deadline = now,
            status = TaskStatus.DRAFT,
        )
        whenever(taskRepository.findByIdForUpdate(10)).thenReturn(task)
        whenever(assignmentRepository.findAllByTaskId(10)).thenReturn(emptyList())

        assertThat(processor.archiveIfOverdue(10, now)).isTrue()
        assertThat(task.status).isEqualTo(TaskStatus.ARCHIVED)
    }

    @Test
    fun `repeated processing is idempotent`() {
        val task = AssessmentTask(
            id = 10,
            deadline = now.minusSeconds(1),
            status = TaskStatus.SENT,
        )
        whenever(taskRepository.findByIdForUpdate(10)).thenReturn(task)
        whenever(assignmentRepository.findAllByTaskId(10)).thenReturn(emptyList())

        assertThat(processor.archiveIfOverdue(10, now)).isTrue()
        assertThat(processor.archiveIfOverdue(10, now)).isFalse()

        verify(operationLogRepository, times(1)).save(any())
    }
}
