package com.acme.assessment.service

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.service.AuthenticationService
import com.acme.assessment.dto.CurrentUser
import com.acme.assessment.entity.AssessmentTemplate
import com.acme.assessment.entity.AssessmentTemplateVersion
import com.acme.assessment.entity.Department
import com.acme.assessment.entity.JobPosition
import com.acme.assessment.entity.RecordStatus
import com.acme.assessment.entity.TemplateStatus
import com.acme.assessment.entity.TemplateVersionStatus
import com.acme.assessment.entity.UserStatus
import com.acme.assessment.entity.User
import com.acme.assessment.repository.AssessmentTemplateRepository
import com.acme.assessment.repository.AssessmentTemplateVersionRepository
import com.acme.assessment.repository.AssessmentTaskRepository
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
    private val taskRepository: AssessmentTaskRepository,
    private val objectMapper: ObjectMapper,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
    private val dingTalkUserService: DingTalkUserService,
) {
    @Transactional(readOnly = true)
    fun listPendingUsers(): List<PendingUserResponse> {
        requireRole("ADMIN")
        val roles = roleRepository.findAll().associateBy { it.id }
        return userRepository.findAllByStatusOrderByCreatedAtAsc(UserStatus.PENDING_APPROVAL).map {
            PendingUserResponse(
                id = requireNotNull(it.id), username = it.username, realName = it.realName, phone = it.phone,
                requestedRole = roles[it.roleId]?.roleCode ?: "UNKNOWN", dingtalkUserId = it.dingtalkUserId,
                dingtalkName = it.dingtalkName, dingtalkMatchStatus = it.dingtalkMatchStatus, createdAt = it.createdAt,
            )
        }
    }

    @Transactional
    fun approveUser(userId: Long): UserResponse {
        val actor = requireRole("ADMIN")
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("用户") }
        if (user.status != UserStatus.PENDING_APPROVAL) {
            throw ConflictException("USER_NOT_PENDING", "该用户不在待审核状态")
        }
        user.status = UserStatus.ACTIVE
        user.updatedBy = actor.id
        user.updatedAt = clock.instant()
        return userRepository.save(user).toResponse()
    }

    @Transactional
    fun matchDingtalkUser(userId: Long): PendingUserResponse {
        requireRole("ADMIN")
        val user = userRepository.findById(userId).orElseThrow { NotFoundException("用户") }
        dingTalkUserService.resolveAndCache(user)
        val role = roleRepository.findById(user.roleId).orElse(null)
        return PendingUserResponse(
            id = requireNotNull(user.id), username = user.username, realName = user.realName, phone = user.phone,
            requestedRole = role?.roleCode ?: "UNKNOWN", dingtalkUserId = user.dingtalkUserId,
            dingtalkName = user.dingtalkName, dingtalkMatchStatus = user.dingtalkMatchStatus, createdAt = user.createdAt,
        )
    }

    @Transactional
    fun createUser(request: CreateUserRequest): UserResponse {
        val actor = requireRole("ADMIN")
        val username = request.username.trim()
        if (userRepository.existsByUsername(username)) throw ConflictException("USERNAME_EXISTS", "用户名已存在")
        val role = roleRepository.findById(request.roleId).orElseThrow { NotFoundException("角色") }
        request.departmentId?.let {
            departmentRepository.findById(it).orElseThrow { NotFoundException("部门") }
        }
        val position = request.positionId?.let { positionRepository.findById(it).orElseThrow { NotFoundException("岗位") } }
        if (role.roleCode == "REVIEWER") {
            if (position == null) throw BusinessException("REVIEWER_POSITION_REQUIRED", "评估人员必须绑定岗位")
            if (request.departmentId != position.departmentId) throw BusinessException("POSITION_DEPARTMENT_MISMATCH", "岗位必须属于所选部门")
            if (position.status != RecordStatus.ACTIVE) throw BusinessException("POSITION_INACTIVE", "只能绑定启用岗位")
        } else if (request.positionId != null) {
            throw BusinessException("POSITION_ONLY_FOR_REVIEWER", "只有评估人员可以绑定岗位")
        }
        val now = clock.instant()
        return userRepository.save(User(
            username = username,
            passwordHash = passwordEncoder.encode(request.password),
            realName = request.realName.trim(),
            roleId = request.roleId,
            departmentId = request.departmentId,
            positionId = request.positionId,
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
    fun listReviewers(positionId: Long? = null): List<ReviewerResponse> {
        requireRole("HR", "HR_MANAGER", "ADMIN")
        val reviewerRole = roleRepository.findByRoleCode("REVIEWER") ?: throw NotFoundException("REVIEWER角色")
        val reviewerRoleId = requireNotNull(reviewerRole.id)
        val users = if (positionId == null) userRepository.findAll().filter { it.roleId == reviewerRoleId && it.status == UserStatus.ACTIVE }
        else userRepository.findAllByRoleIdAndPositionIdAndStatus(reviewerRoleId, positionId, UserStatus.ACTIVE)
        return users.sortedWith(compareBy<User> { it.realName.lowercase() }.thenBy { it.id ?: Long.MAX_VALUE })
            .map { ReviewerResponse(requireNotNull(it.id), it.username, it.realName, it.departmentId, it.positionId) }
    }

    @Transactional
    fun createTemplate(request: CreateTemplateRequest): TemplateResponse {
        val actor = requireRole("HR", "HR_MANAGER", "REVIEWER", "ADMIN")
        validateJson(request.schemaJson)
        positionRepository.findById(request.positionId).orElseThrow { NotFoundException("招聘岗位") }
        if (templateRepository.existsByPositionId(request.positionId)) {
            throw ConflictException("POSITION_TEMPLATE_EXISTS", "该岗位已有测评模板，请通过替换模板生成新版本")
        }
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
        return versionRepository.findAllByTemplateId(templateId)
            .sortedByDescending { it.versionNo }
            .map { it.toResponse() }
    }

    @Transactional
    fun createTemplateVersion(templateId: Long, request: CreateTemplateVersionRequest): TemplateVersionResponse {
        val actor = requireRole("HR", "HR_MANAGER", "REVIEWER", "ADMIN")
        validateJson(request.schemaJson)
        templateRepository.findById(templateId).orElseThrow { NotFoundException("测评模板") }
        val existing = versionRepository.findAllByTemplateId(templateId)
        val now = clock.instant()
        return versionRepository.save(AssessmentTemplateVersion(
            templateId = templateId,
            versionNo = (existing.maxOfOrNull { it.versionNo } ?: 0) + 1,
            schemaJson = request.schemaJson,
            versionStatus = TemplateVersionStatus.DRAFT,
            createdBy = actor.id,
            updatedBy = actor.id,
            createdAt = now,
            updatedAt = now,
        )).toResponse()
    }

    @Transactional(readOnly = true)
    fun listTemplates(): List<TemplateListResponse> = templateRepository.findAll().map {
        TemplateListResponse(requireNotNull(it.id), it.templateName, it.positionId, it.ownerUserId, it.status)
    }

    @Transactional
    fun publishVersion(versionId: Long): PublishVersionResponse {
        val actor = requireRole("HR_MANAGER", "ADMIN")
        val version = versionRepository.findById(versionId).orElseThrow { NotFoundException("模板版本") }
        if (version.versionStatus !in setOf(TemplateVersionStatus.DRAFT, TemplateVersionStatus.PENDING, TemplateVersionStatus.ARCHIVED)) {
            throw ConflictException("INVALID_VERSION_STATUS", "只有草稿、待审核或历史版本可以发布")
        }
        val template = templateRepository.findById(version.templateId).orElseThrow { NotFoundException("测评模板") }
        val now = clock.instant()
        versionRepository.findAllByTemplateId(version.templateId)
            .filter { it.id != version.id && it.versionStatus == TemplateVersionStatus.PUBLISHED }
            .forEach { published ->
                published.versionStatus = TemplateVersionStatus.ARCHIVED
                published.updatedBy = actor.id
                published.updatedAt = now
                versionRepository.save(published)
            }
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

    @Transactional
    fun deleteVersion(versionId: Long) {
        requireRole("HR_MANAGER", "ADMIN")
        val version = versionRepository.findById(versionId).orElseThrow { NotFoundException("模板版本") }
        if (version.versionStatus == TemplateVersionStatus.PUBLISHED) {
            throw ConflictException("PUBLISHED_VERSION_CANNOT_DELETE", "当前发布版本不能删除，请先发布其他版本")
        }
        if (versionRepository.findAllByTemplateId(version.templateId).size <= 1) {
            throw ConflictException("LAST_VERSION_CANNOT_DELETE", "模板至少需要保留一个版本")
        }
        if (taskRepository.existsByTemplateVersionId(versionId)) {
            throw ConflictException("VERSION_IN_USE", "该版本已被测评任务使用，无法删除")
        }
        versionRepository.delete(version)
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
    private fun User.toResponse() = UserResponse(
        requireNotNull(id), username, realName, roleId, departmentId, positionId, status.name,
        phone, dingtalkUserId, dingtalkName, dingtalkMatchStatus,
    )
}
