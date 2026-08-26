package com.acme.assessment.task

import com.acme.assessment.auth.AuthenticationService
import com.acme.assessment.auth.CurrentUser
import com.acme.assessment.config.AppProperties
import com.acme.assessment.config.BootstrapProperties
import com.acme.assessment.config.JwtProperties
import com.acme.assessment.domain.AssessmentTask
import com.acme.assessment.domain.AssessmentAssignment
import com.acme.assessment.domain.AssessmentTemplate
import com.acme.assessment.domain.AssessmentTemplateVersion
import com.acme.assessment.domain.JobPosition
import com.acme.assessment.domain.RecordStatus
import com.acme.assessment.domain.Role
import com.acme.assessment.domain.TemplateVersionStatus
import com.acme.assessment.domain.User
import com.acme.assessment.domain.UserStatus
import com.acme.assessment.repository.AssessmentAssignmentRepository
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.AssessmentTemplateRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.JobPositionRepository
import com.acme.assessment.repository.OperationLogRepository
import com.acme.assessment.repository.RoleRepository
import com.acme.assessment.repository.UserRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class AssessmentTaskServiceTest {
    private val authenticationService = mock<AuthenticationService>()
    private val taskRepository = mock<AssessmentTaskRepository>()
    private val assignmentRepository = mock<AssessmentAssignmentRepository>()
    private val positionRepository = mock<JobPositionRepository>()
    private val templateRepository = mock<AssessmentTemplateRepository>()
    private val versionRepository = mock<AssessmentTemplateVersionRepository>()
    private val userRepository = mock<UserRepository>()
    private val roleRepository = mock<RoleRepository>()
    private val operationLogRepository = mock<OperationLogRepository>()
    private val tokenService = mock<SecureTokenService>()
    private val numberGenerator = mock<TaskNumberGenerator>()
    private val now = Instant.parse("2026-08-25T03:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private lateinit var service: AssessmentTaskService

    @BeforeEach
    fun setUp() {
        service = AssessmentTaskService(
            authenticationService,
            taskRepository,
            assignmentRepository,
            positionRepository,
            templateRepository,
            versionRepository,
            userRepository,
            roleRepository,
            operationLogRepository,
            tokenService,
            numberGenerator,
            jacksonObjectMapper(),
            AppProperties(
                JwtProperties("test", "12345678901234567890123456789012", Duration.ofHours(1)),
                "https://example.test/assessment",
                BootstrapProperties("admin", "secret"),
            ),
            clock,
        )
    }

    @Test
    fun `creates draft task assignments and audit log atomically`() {
        whenever(authenticationService.currentUser()).thenReturn(CurrentUser(10, "hr", "招聘专员", "HR", 1))
        whenever(positionRepository.findById(100)).thenReturn(
            Optional.of(JobPosition(id = 100, departmentId = 1, positionCode = "DEV", positionName = "开发", status = RecordStatus.ACTIVE)),
        )
        whenever(versionRepository.findById(300)).thenReturn(
            Optional.of(AssessmentTemplateVersion(id = 300, templateId = 200, versionStatus = TemplateVersionStatus.PUBLISHED)),
        )
        whenever(templateRepository.findById(200)).thenReturn(
            Optional.of(AssessmentTemplate(id = 200, positionId = 100)),
        )
        whenever(userRepository.findAllByIdIn(setOf(20L))).thenReturn(
            listOf(User(id = 20, username = "reviewer", realName = "评估员", roleId = 4, status = UserStatus.ACTIVE)),
        )
        whenever(roleRepository.findAllById(listOf(4L))).thenReturn(listOf(Role(id = 4, roleCode = "REVIEWER")))
        whenever(numberGenerator.next()).thenReturn("TEST202608250001")
        whenever(taskRepository.existsByTaskNo(any())).thenReturn(false)
        whenever(tokenService.generate()).thenReturn("raw-token")
        whenever(tokenService.hash("raw-token")).thenReturn("hashed-token")
        whenever(taskRepository.existsByTokenHash("hashed-token")).thenReturn(false)
        whenever(taskRepository.save(any<AssessmentTask>())).thenAnswer {
            it.getArgument<AssessmentTask>(0).apply { id = 500 }
        }

        val response = service.create(
            CreateAssessmentTaskRequest(
                candidateName = " 张三 ",
                candidatePhone = " 13800000000 ",
                positionId = 100,
                templateVersionId = 300,
                reviewerUserIds = setOf(20),
                deadline = now.plusSeconds(3600),
            ),
        )

        assertThat(response.id).isEqualTo(500)
        assertThat(response.assessmentUrl).isEqualTo("https://example.test/assessment/raw-token")
        val task = argumentCaptor<AssessmentTask>()
        verify(taskRepository).save(task.capture())
        assertThat(task.firstValue.candidateName).isEqualTo("张三")
        assertThat(task.firstValue.tokenHash).isEqualTo("hashed-token")
        verify(assignmentRepository).saveAll(any<Iterable<AssessmentAssignment>>())
        verify(operationLogRepository).save(any())
    }
}
