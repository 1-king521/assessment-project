package com.acme.assessment.service

import com.acme.assessment.dto.CurrentUser
import com.acme.assessment.dto.UpdatePositionRequest
import com.acme.assessment.dto.UpdatePositionStatusRequest
import com.acme.assessment.entity.RecordStatus
import com.acme.assessment.entity.RecruitmentPosition
import com.acme.assessment.repository.AssessmentTaskRepository
import com.acme.assessment.repository.AssessmentTemplateRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.DepartmentRepository
import com.acme.assessment.repository.JobPositionRepository
import com.acme.assessment.repository.RecruitmentPositionRepository
import com.acme.assessment.repository.RoleRepository
import com.acme.assessment.repository.UserRepository
import com.acme.assessment.web.ConflictException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class AdminServicePositionTest {
    private val authenticationService = mock<AuthenticationService>()
    private val recruitmentPositionRepository = mock<RecruitmentPositionRepository>()
    private val templateRepository = mock<AssessmentTemplateRepository>()
    private val taskRepository = mock<AssessmentTaskRepository>()
    private val now = Instant.parse("2026-09-15T04:00:00Z")
    private lateinit var service: AdminService

    @BeforeEach
    fun setUp() {
        service = AdminService(
            authenticationService = authenticationService,
            departmentRepository = mock<DepartmentRepository>(),
            userRepository = mock<UserRepository>(),
            roleRepository = mock<RoleRepository>(),
            positionRepository = mock<JobPositionRepository>(),
            recruitmentPositionRepository = recruitmentPositionRepository,
            templateRepository = templateRepository,
            versionRepository = mock<AssessmentTemplateVersionRepository>(),
            taskRepository = taskRepository,
            objectMapper = jacksonObjectMapper(),
            passwordEncoder = mock<PasswordEncoder>(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
            dingTalkUserService = mock<DingTalkUserService>(),
        )
        whenever(authenticationService.currentUser()).thenReturn(CurrentUser(7, "hr", "招聘专员", "HR", null))
    }

    @Test
    fun `hr can update recruitment position`() {
        val position = RecruitmentPosition(id = 10, departmentName = "旧部门", positionName = "旧岗位")
        whenever(recruitmentPositionRepository.findById(10)).thenReturn(Optional.of(position))
        whenever(recruitmentPositionRepository.save(position)).thenReturn(position)

        val response = service.updatePosition(10, UpdatePositionRequest(" 技术部 ", " Java工程师 "))

        assertThat(response.departmentName).isEqualTo("技术部")
        assertThat(response.positionName).isEqualTo("Java工程师")
        assertThat(position.updatedBy).isEqualTo(7)
        assertThat(position.updatedAt).isEqualTo(now)
    }

    @Test
    fun `hr can disable recruitment position`() {
        val position = RecruitmentPosition(id = 10)
        whenever(recruitmentPositionRepository.findById(10)).thenReturn(Optional.of(position))
        whenever(recruitmentPositionRepository.save(position)).thenReturn(position)

        val response = service.updatePositionStatus(10, UpdatePositionStatusRequest(RecordStatus.INACTIVE))

        assertThat(response.status).isEqualTo(RecordStatus.INACTIVE)
        assertThat(position.updatedBy).isEqualTo(7)
    }

    @Test
    fun `referenced recruitment position cannot be deleted`() {
        val position = RecruitmentPosition(id = 10)
        whenever(recruitmentPositionRepository.findById(10)).thenReturn(Optional.of(position))
        whenever(templateRepository.existsByPositionId(10)).thenReturn(true)

        assertThatThrownBy { service.deletePosition(10) }
            .isInstanceOf(ConflictException::class.java)
            .hasMessage("岗位已被模板或测评任务使用，请改为禁用")
        verify(recruitmentPositionRepository, never()).delete(any())
    }
}
