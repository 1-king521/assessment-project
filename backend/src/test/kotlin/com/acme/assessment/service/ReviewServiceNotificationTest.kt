package com.acme.assessment.service

import com.acme.assessment.dto.CurrentUser
import com.acme.assessment.dto.SubmitReviewRequest
import com.acme.assessment.entity.AssessmentAssignment
import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.ReviewConclusion
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.repository.AssessmentAnswerRepository
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentFileRepository
import com.acme.assessment.repository.AssessmentReviewRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.RecruitmentPositionRepository
import com.acme.assessment.repository.OperationLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class ReviewServiceNotificationTest {
    private val authenticationService = mock<AuthenticationService>()
    private val assignmentRepository = mock<AssessmentAssignmentRepository>()
    private val taskRepository = mock<AssessmentTaskRepository>()
    private val templateVersionRepository = mock<AssessmentTemplateVersionRepository>()
    private val reviewRepository = mock<AssessmentReviewRepository>()
    private val answerRepository = mock<AssessmentAnswerRepository>()
    private val fileRepository = mock<AssessmentFileRepository>()
    private val positionRepository = mock<RecruitmentPositionRepository>()
    private val operationLogRepository = mock<OperationLogRepository>()
    private val fileStorageService = mock<FileStorageService>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val now = Instant.parse("2026-09-04T08:00:00Z")
    private lateinit var service: ReviewService

    @BeforeEach
    fun setUp() {
        service = ReviewService(
            authenticationService, assignmentRepository, taskRepository,
            templateVersionRepository, reviewRepository, answerRepository,
            fileRepository, positionRepository, operationLogRepository,
            fileStorageService, Clock.fixed(now, ZoneOffset.UTC), eventPublisher,
        )
        whenever(authenticationService.currentUser()).thenReturn(
            CurrentUser(7, "reviewer", "评估人员", "REVIEWER", null)
        )
    }

    @Test
    fun `does not notify hr before the last active reviewer completes`() {
        val mine = assignment(11, 7, AssignmentStatus.IN_PROGRESS)
        val other = assignment(12, 8, AssignmentStatus.IN_PROGRESS)
        stubSubmission(mine, listOf(mine, other))

        service.submit(11, SubmitReviewRequest(ReviewConclusion.PASS))

        verify(eventPublisher, never()).publishEvent(any<AllReviewsCompletedEvent>())
        verify(taskRepository).findByIdForUpdate(5)
    }

    @Test
    fun `notifies hr once when all active reviewers complete and ignores cancelled assignments`() {
        val mine = assignment(11, 7, AssignmentStatus.IN_PROGRESS)
        val completed = assignment(12, 8, AssignmentStatus.COMPLETED)
        val cancelled = assignment(13, 9, AssignmentStatus.CANCELLED)
        val task = stubSubmission(mine, listOf(mine, completed, cancelled))

        service.submit(11, SubmitReviewRequest(ReviewConclusion.PASS))

        assertThat(task.status).isEqualTo(TaskStatus.REVIEWED)
        val event = argumentCaptor<AllReviewsCompletedEvent>()
        verify(eventPublisher).publishEvent(event.capture())
        assertThat(event.firstValue).isEqualTo(AllReviewsCompletedEvent(5, 20, 2))
    }

    private fun stubSubmission(
        mine: AssessmentAssignment,
        assignments: List<AssessmentAssignment>,
    ): AssessmentTask {
        val task = AssessmentTask(id = 5, hrUserId = 20, status = TaskStatus.REVIEWING)
        whenever(assignmentRepository.findById(mine.id!!)).thenReturn(Optional.of(mine))
        whenever(reviewRepository.findByAssignmentId(mine.id!!)).thenReturn(null)
        whenever(taskRepository.findByIdForUpdate(5)).thenReturn(task)
        whenever(assignmentRepository.findAllByTaskId(5)).thenReturn(assignments)
        return task
    }

    private fun assignment(id: Long, reviewerId: Long, status: AssignmentStatus) =
        AssessmentAssignment(id = id, taskId = 5, reviewerUserId = reviewerId, status = status)
}

