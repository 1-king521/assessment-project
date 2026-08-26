package com.acme.assessment.admin

import com.acme.assessment.auth.AuthenticationService
import com.acme.assessment.auth.CurrentUser
import com.acme.assessment.domain.AssessmentTemplate
import com.acme.assessment.domain.AssessmentTemplateVersion
import com.acme.assessment.domain.Department
import com.acme.assessment.domain.JobPosition
import com.acme.assessment.domain.RecordStatus
import com.acme.assessment.domain.TemplateStatus
import com.acme.assessment.domain.TemplateVersionStatus
import com.acme.assessment.domain.UserStatus
import com.acme.assessment.domain.User
import com.acme.assessment.repository.AssessmentTemplateRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.DepartmentRepository
import com.acme.assessment.repository.JobPositionRepository
import com.acme.assessment.repository.RoleRepository
import com.acme.assessment.repository.UserRepository
import com.acme.assessment.web.BusinessException
import com.acme.assessment.web.ConflictException
import com.acme.assessment.web.NotFoundException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class AdminService(
    private val authenticationService: AuthenticationService,
    private val departmentRepository: DepartmentRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val positionRepository: JobPositionRepository,
    private val templateRepository: AssessmentTemplateRepository,
    private val versionRepository: AssessmentTemplateVersionRepository,
    private val objectMapper: ObjectMapper,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
) {
    @Transactional
    fun createUser(request: CreateUserRequest): UserResponse {
        val actor = requireRole("ADMIN")
        val username = request.username.trim()
        if (userRepository.existsByUsername(username)) throw ConflictException("USERNAME_EXISTS", "用户名已存在")
        roleRepository.findById(request.roleId).orElseThrow { NotFoundException("角色") }
        request.departmentId?.let {
            departmentRepository.findById(it).orElseThrow { NotFoundException("部门") }
        }
        val now = clock.instant()
        return userRepository.save(User(
            username = username,
            passwordHash = passwordEncoder.encode(request.password),
            realName = request.realName.trim(),
            roleId = request.roleId,
            departmentId = request.departmentId,
            email = request.email?.trim()?.takeIf { it.isNotEmpty() },
            phone = request.phone?.trim()?.takeIf { it.isNotEmpty() },
            status = UserStatus.ACTIVE,
            createdBy = actor.id,
            updatedBy = actor.id,
            createdAt = now,
            updatedAt = now,
        )).toResponse()
    }

    @Transactional
    fun createDepartment(request: CreateDepartmentRequest): DepartmentResponse {
        val actor = requireRole("ADMIN")
        val code = request.departmentCode.trim()
        val name = request.departmentName.trim()
        if (departmentRepository.existsByDepartmentCode(code)) {
            throw ConflictException("DEPARTMENT_CODE_EXISTS", "部门编码已存在")
        }
        if (departmentRepository.existsByDepartmentName(name)) {
            throw ConflictException("DEPARTMENT_NAME_EXISTS", "部门名称已存在")
        }
        val now = clock.instant()
        return departmentRepository.save(Department(
            departmentCode = code,
            departmentName = name,
            description = request.description?.trim(),
            createdBy = actor.id,
            updatedBy = actor.id,
            createdAt = now,
            updatedAt = now,
        )).toResponse()
    }

    @Transactional(readOnly = true)
    fun listDepartments(): List<DepartmentResponse> = departmentRepository.findAll().map { it.toResponse() }

    @Transactional
    fun createPosition(request: CreatePositionRequest): PositionResponse {
        val actor = requireRole("HR_MANAGER", "ADMIN")
        val department = departmentRepository.findById(request.departmentId)
            .orElseThrow { NotFoundException("部门") }
        if (department.status != RecordStatus.ACTIVE) throw ConflictException("DEPARTMENT_INACTIVE", "部门未启用")
        val code = request.positionCode.trim()
        if (positionRepository.findAll().any { it.positionCode == code }) {
            throw ConflictException("POSITION_CODE_EXISTS", "岗位编码已存在")
        }
        val now = clock.instant()
        return positionRepository.save(JobPosition(
            departmentId = requireNotNull(department.id),
            positionCode = code,
            positionName = request.positionName.trim(),
            description = request.description?.trim(),
            createdBy = actor.id,
            updatedBy = actor.id,
            createdAt = now,
            updatedAt = now,
        )).toResponse()
    }

    @Transactional(readOnly = true)
    fun listPositions(): List<PositionResponse> = positionRepository.findAll().map { it.toResponse() }

    @Transactional(readOnly = true)
    fun listReviewers(): List<ReviewerResponse> {
        requireRole("HR", "HR_MANAGER", "ADMIN")
        val reviewerRole = roleRepository.findByRoleCode("REVIEWER") ?: throw NotFoundException("REVIEWER角色")
        return userRepository.findAll()
            .filter { it.roleId == reviewerRole.id && it.status == UserStatus.ACTIVE }
            .map { ReviewerResponse(requireNotNull(it.id), it.username, it.realName, it.departmentId) }
    }

    @Transactional
    fun createTemplate(request: CreateTemplateRequest): TemplateResponse {
        val actor = requireRole("HR", "HR_MANAGER", "REVIEWER", "ADMIN")
        validateJson(request.schemaJson)
        positionRepository.findById(request.positionId).orElseThrow { NotFoundException("招聘岗位") }
        val ownerId = request.ownerUserId ?: actor.id
        userRepository.findById(ownerId).orElseThrow { NotFoundException("模板负责人") }
        val now = clock.instant()
        val template = templateRepository.save(AssessmentTemplate(
            templateName = request.templateName.trim(),
            positionId = request.positionId,
            ownerUserId = ownerId,
            status = TemplateStatus.DRAFT,
            createdBy = actor.id,
            updatedBy = actor.id,
            createdAt = now,
            updatedAt = now,
        ))
        val version = versionRepository.save(AssessmentTemplateVersion(
            templateId = requireNotNull(template.id),
            versionNo = 1,
            schemaJson = request.schemaJson,
            versionStatus = TemplateVersionStatus.DRAFT,
            createdBy = actor.id,
            updatedBy = actor.id,
            createdAt = now,
            updatedAt = now,
        ))
        return TemplateResponse(requireNotNull(template.id), template.templateName, template.positionId,
            template.ownerUserId, template.status, requireNotNull(version.id))
    }

    @Transactional(readOnly = true)
    fun listTemplateVersions(templateId: Long): List<TemplateVersionResponse> {
        templateRepository.findById(templateId).orElseThrow { NotFoundException("测评模板") }
        return versionRepository.findAll().filter { it.templateId == templateId }.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun listTemplates(): List<TemplateListResponse> = templateRepository.findAll().map {
        TemplateListResponse(requireNotNull(it.id), it.templateName, it.positionId, it.ownerUserId, it.status)
    }

    @Transactional
    fun publishVersion(versionId: Long): PublishVersionResponse {
        val actor = requireRole("HR_MANAGER", "ADMIN")
        val version = versionRepository.findById(versionId).orElseThrow { NotFoundException("模板版本") }
        if (version.versionStatus != TemplateVersionStatus.DRAFT && version.versionStatus != TemplateVersionStatus.PENDING) {
            throw ConflictException("INVALID_VERSION_STATUS", "只有草稿或待审核版本可以发布")
        }
        val template = templateRepository.findById(version.templateId).orElseThrow { NotFoundException("测评模板") }
        val now = clock.instant()
        version.versionStatus = TemplateVersionStatus.PUBLISHED
        version.publishedBy = actor.id
        version.publishedAt = now
        version.updatedBy = actor.id
        version.updatedAt = now
        template.status = TemplateStatus.ACTIVE
        template.updatedBy = actor.id
        template.updatedAt = now
        versionRepository.save(version)
        templateRepository.save(template)
        return PublishVersionResponse(requireNotNull(version.id), version.templateId, version.versionNo, version.versionStatus)
    }

    private fun requireRole(vararg roles: String): CurrentUser {
        val actor = authenticationService.currentUser()
        if (!actor.isAnyRole(*roles)) throw BusinessException("FORBIDDEN", "当前用户无权执行该操作", HttpStatus.FORBIDDEN)
        return actor
    }

    private fun validateJson(schema: String) {
        try {
            objectMapper.readTree(schema)
        } catch (_: Exception) {
            throw BusinessException("INVALID_SCHEMA_JSON", "模板结构必须是合法 JSON")
        }
    }

    private fun Department.toResponse() = DepartmentResponse(requireNotNull(id), departmentCode, departmentName, description, status)
    private fun JobPosition.toResponse() = PositionResponse(requireNotNull(id), departmentId, positionCode, positionName, description, defaultTemplateId, status)
    private fun AssessmentTemplateVersion.toResponse() = TemplateVersionResponse(requireNotNull(id), templateId, versionNo, schemaJson, versionStatus)
    private fun User.toResponse() = UserResponse(requireNotNull(id), username, realName, roleId, departmentId, status.name)
}
