package com.acme.assessment.service

import com.acme.assessment.dto.CurrentUser
import com.acme.assessment.entity.AssessmentTemplate
import com.acme.assessment.entity.AssessmentTemplateVersion
import com.acme.assessment.entity.TemplateVersionStatus
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class AdminServiceVersionTest {
    private val authenticationService = mock<AuthenticationService>()
    private val departmentRepository = mock<DepartmentRepository>()
    private val userRepository = mock<UserRepository>()
    private val roleRepository = mock<RoleRepository>()
    private val positionRepository = mock<JobPositionRepository>()
    private val recruitmentPositionRepository = mock<RecruitmentPositionRepository>()
    private val templateRepository = mock<AssessmentTemplateRepository>()
    private val versionRepository = mock<AssessmentTemplateVersionRepository>()
    private val taskRepository = mock<AssessmentTaskRepository>()
    private val passwordEncoder = mock<PasswordEncoder>()
    private val dingTalkUserService = mock<DingTalkUserService>()
    private val now = Instant.parse("2026-08-29T06:00:00Z")
    private lateinit var service: AdminService

    @BeforeEach
    fun setUp() {
        service = AdminService(
            authenticationService,
            departmentRepository,
            userRepository,
            roleRepository,
            positionRepository,
            recruitmentPositionRepository,
            templateRepository,
            versionRepository,
            taskRepository,
            jacksonObjectMapper(),
            passwordEncoder,
            Clock.fixed(now, ZoneOffset.UTC),
            dingTalkUserService,
        )
        whenever(authenticationService.currentUser()).thenReturn(CurrentUser(1, "admin", "管理员", "ADMIN", null))
    }

    @Test
    fun `publishing archived version archives current published version`() {
        val template = AssessmentTemplate(id = 10)
        val archived = AssessmentTemplateVersion(id = 101, templateId = 10, versionNo = 1, versionStatus = TemplateVersionStatus.ARCHIVED)
        val published = AssessmentTemplateVersion(id = 102, templateId = 10, versionNo = 2, versionStatus = TemplateVersionStatus.PUBLISHED)
        whenever(versionRepository.findById(101)).thenReturn(Optional.of(archived))
        whenever(templateRepository.findById(10)).thenReturn(Optional.of(template))
        whenever(versionRepository.findAllByTemplateId(10)).thenReturn(listOf(archived, published))

        val response = service.publishVersion(101)

        assertThat(response.status).isEqualTo(TemplateVersionStatus.PUBLISHED)
        assertThat(archived.versionStatus).isEqualTo(TemplateVersionStatus.PUBLISHED)
        assertThat(published.versionStatus).isEqualTo(TemplateVersionStatus.ARCHIVED)
        verify(versionRepository).save(published)
        verify(versionRepository).save(archived)
    }

    @Test
    fun `referenced version cannot be deleted`() {
        val version = AssessmentTemplateVersion(id = 101, templateId = 10, versionNo = 1, versionStatus = TemplateVersionStatus.ARCHIVED)
        whenever(versionRepository.findById(101)).thenReturn(Optional.of(version))
        whenever(versionRepository.findAllByTemplateId(10)).thenReturn(listOf(version, AssessmentTemplateVersion(id = 102, templateId = 10)))
        whenever(taskRepository.existsByTemplateVersionId(101)).thenReturn(true)

        assertThatThrownBy { service.deleteVersion(101) }
            .isInstanceOf(ConflictException::class.java)
            .hasMessage("该版本已被测评任务使用，无法删除")
    }
}
