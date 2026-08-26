package com.acme.assessment.admin

import com.acme.assessment.domain.RecordStatus
import com.acme.assessment.domain.TemplateStatus
import com.acme.assessment.domain.TemplateVersionStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CreateUserRequest(
    @field:NotBlank @field:Size(max = 80) val username: String,
    @field:NotBlank @field:Size(min = 8, max = 128) val password: String,
    @field:NotBlank @field:Size(max = 80) val realName: String,
    @field:Positive val roleId: Long,
    @field:Positive val departmentId: Long? = null,
    @field:Size(max = 120) val email: String? = null,
    @field:Size(max = 30) val phone: String? = null,
)

data class UserResponse(
    val id: Long,
    val username: String,
    val realName: String,
    val roleId: Long,
    val departmentId: Long?,
    val status: String,
)

data class CreateDepartmentRequest(
    @field:NotBlank val departmentCode: String,
    @field:NotBlank val departmentName: String,
    @field:Size(max = 255) val description: String? = null,
)

data class DepartmentResponse(
    val id: Long,
    val departmentCode: String,
    val departmentName: String,
    val description: String?,
    val status: RecordStatus,
)

data class CreatePositionRequest(
    @field:Positive val departmentId: Long,
    @field:NotBlank val positionCode: String,
    @field:NotBlank val positionName: String,
    val description: String? = null,
)

data class PositionResponse(
    val id: Long,
    val departmentId: Long,
    val positionCode: String,
    val positionName: String,
    val description: String?,
    val defaultTemplateId: Long?,
    val status: RecordStatus,
)

data class ReviewerResponse(
    val id: Long,
    val username: String,
    val realName: String,
    val departmentId: Long?,
)

data class CreateTemplateRequest(
    @field:NotBlank val templateName: String,
    @field:Positive val positionId: Long,
    @field:Positive val ownerUserId: Long? = null,
    @field:NotBlank val schemaJson: String,
)

data class TemplateResponse(
    val id: Long,
    val templateName: String,
    val positionId: Long,
    val ownerUserId: Long,
    val status: TemplateStatus,
    val initialVersionId: Long,
)

data class TemplateListResponse(
    val id: Long,
    val templateName: String,
    val positionId: Long,
    val ownerUserId: Long,
    val status: TemplateStatus,
)

data class TemplateVersionResponse(
    val id: Long,
    val templateId: Long,
    val versionNo: Int,
    val schemaJson: String,
    val status: TemplateVersionStatus,
)

data class PublishVersionResponse(
    val id: Long,
    val templateId: Long,
    val versionNo: Int,
    val status: TemplateVersionStatus,
)
