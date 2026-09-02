package com.acme.assessment.service

import com.acme.assessment.dto.AssignReviewersRequest
import com.acme.assessment.dto.CurrentUser
import com.acme.assessment.entity.AssessmentAssignment
import com.acme.assessment.entity.AssessmentTask
import com.acme.assessment.entity.AssessmentTemplateVersion
import com.acme.assessment.entity.AssignmentStatus
import com.acme.assessment.entity.JobPosition
import com.acme.assessment.entity.Role
import com.acme.assessment.entity.ReviewConclusion
import com.acme.assessment.entity.TaskStatus
import com.acme.assessment.entity.User
import com.acme.assessment.entity.UserStatus
import com.acme.assessment.repository.AssessmentAnswerRepository
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentFileRepository
import com.acme.assessment.repository.AssessmentReviewRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.JobPositionRepository
import com.acme.assessment.repository.OperationLogRepository
import com.acme.assessment.repository.RoleRepository
import com.acme.assessment.repository.UserRepository
import com.acme.assessment.web.ConflictException
import com.acme.assessment.web.BusinessException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class TaskManagementAssignmentTest {
    private val authenticationService = mock<AuthenticationService>()
    private val taskRepository = mock<AssessmentTaskRepository>()
    private val assignmentRepository = mock<AssessmentAssignmentRepository>()
    private val answerRepository = mock<AssessmentAnswerRepository>()
    private val fileRepository = mock<AssessmentFileRepository>()
    private val reviewRepository = mock<AssessmentReviewRepository>()
    private val positionRepository = mock<JobPositionRepository>()
    private val versionRepository = mock<AssessmentTemplateVersionRepository>()
    private val userRepository = mock<UserRepository>()
    private val roleRepository = mock<RoleRepository>()
    private val operationLogRepository = mock<OperationLogRepository>()
    private val fileStorageService = mock<FileStorageService>()
    private val now = Instant.parse("2026-08-29T07:00:00Z")
    private lateinit var service: TaskManagementService
    private lateinit var task: AssessmentTask

    @BeforeEach
    fun setUp() {
        service = TaskManagementService(
            authenticationService,
            taskRepository,
            assignmentRepository,
            answerRepository,
            fileRepository,
            reviewRepository,
            positionRepository,
            versionRepository,
            userRepository,
            roleRepository,
            operationLogRepository,
            jacksonObjectMapper(),
            fileStorageService,
            Clock.fixed(now, ZoneOffset.UTC),
        )
        task = AssessmentTask(id = 10, taskNo = "TASK-10", hrUserId = 1, positionId = 20, templateVersionId = 30, status = TaskStatus.SUBMITTED)
        whenever(authenticationService.currentUser()).thenReturn(CurrentUser(1, "hr", "招聘专员", "HR", 1))
        whenever(taskRepository.findByIdForUpdate(10)).thenReturn(task)
    }

    @Test
    fun `submitted task can be assigned to active reviewers`() {
        whenever(assignmentRepository.findAllByTaskId(10)).thenReturn(emptyList())
        whenever(userRepository.findAllByIdIn(setOf(40L))).thenReturn(listOf(User(id = 40, roleId = 50, positionId = 20, status = UserStatus.ACTIVE)))
        whenever(roleRepository.findAllById(listOf(50L))).thenReturn(listOf(Role(id = 50, roleCode = "REVIEWER")))
        whenever(positionRepository.findById(20)).thenReturn(Optional.of(JobPosition(id = 20)))
        whenever(versionRepository.findById(30)).thenReturn(Optional.of(AssessmentTemplateVersion(id = 30)))
        whenever(answerRepository.findAllByTaskId(10)).thenReturn(emptyList())
        whenever(fileRepository.findAllByTaskId(10)).thenReturn(emptyList())
        whenever(operationLogRepository.findAll()).thenReturn(emptyList())

        service.assignReviewers(10, AssignReviewersRequest(setOf(40)))

        verify(assignmentRepository).save(any<AssessmentAssignment>())
        verify(assignmentRepository).flush()
    }

    @Test
    fun `reviewer selection is locked after an assignment starts`() {
        whenever(assignmentRepository.findAllByTaskId(10)).thenReturn(
            listOf(AssessmentAssignment(id = 60, taskId = 10, reviewerUserId = 40, status = AssignmentStatus.IN_PROGRESS)),
        )

        assertThatThrownBy { service.assignReviewers(10, AssignReviewersRequest(setOf(40))) }
            .isInstanceOf(ConflictException::class.java)
            .hasMessage("评估已开始，不能再次调整评估人员")
    }

    @Test
    fun `reviewer from another position cannot be assigned`() {
        whenever(assignmentRepository.findAllByTaskId(10)).thenReturn(emptyList())
        whenever(userRepository.findAllByIdIn(setOf(40L))).thenReturn(listOf(User(id = 40, roleId = 50, positionId = 21, status = UserStatus.ACTIVE)))
        whenever(roleRepository.findAllById(listOf(50L))).thenReturn(listOf(Role(id = 50, roleCode = "REVIEWER")))

        assertThatThrownBy { service.assignReviewers(10, AssignReviewersRequest(setOf(40))) }
            .isInstanceOf(BusinessException::class.java)
            .hasMessage("评估人员必须属于当前任务岗位")
    }

    @Test
    fun `abandoned archived task appears in assessment records`() {
        task.status = TaskStatus.ARCHIVED
        task.archivedAt = now
        task.finalConclusion = ReviewConclusion.ABANDONED
        task.finalConclusionAt = now
        task.finalConclusionReason = "截止时间内未提交测评"
        whenever(taskRepository.searchRecords(any(), any(), any(), any(), any()))
            .thenReturn(PageImpl(listOf(task)))
        whenever(positionRepository.findById(20)).thenReturn(Optional.of(JobPosition(id = 20, positionName = "Java开发工程师")))
        whenever(versionRepository.findById(30)).thenReturn(Optional.of(AssessmentTemplateVersion(id = 30, versionNo = 4)))

        val records = service.listRecords(
            page = 1,
            pageSize = 20,
            keyword = null,
            positionId = null,
            conclusion = ReviewConclusion.ABANDONED,
        )

        assertThat(records.total).isEqualTo(1)
        assertThat(records.items.single().conclusions)
            .containsExactly(ReviewConclusion.ABANDONED)
        assertThat(records.items.single().concludedAt).isEqualTo(now)
    }
}
