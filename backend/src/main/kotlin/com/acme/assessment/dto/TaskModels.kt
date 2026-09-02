package com.acme.assessment.dto

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.entity.TaskStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Pattern
import java.time.Instant

data class CreateAssessmentTaskRequest(
    @field:NotBlank(message = "候选人姓名不能为空")
    @field:Size(max = 80, message = "候选人姓名不能超过80个字符")
    val candidateName: String,
    @field:NotBlank(message = "手机号不能为空")
    @field:Size(max = 30, message = "手机号不能超过30个字符")
    @field:Pattern(regexp = "^\\s*\\+?[0-9]{6,30}\\s*$", message = "手机号格式不正确")
    val candidatePhone: String,
    @field:Email(message = "邮箱格式不正确")
    @field:Size(max = 120, message = "邮箱不能超过120个字符")
    val candidateEmail: String? = null,
    @field:Size(max = 50, message = "候选人来源不能超过50个字符")
    val candidateSource: String? = null,
    @field:Positive(message = "岗位 ID 必须为正数")
    val positionId: Long,
    @field:Positive(message = "模板版本 ID 必须为正数")
    val templateVersionId: Long,
    @field:Future(message = "截止时间必须晚于当前时间")
    val deadline: Instant,
)

data class CreateAssessmentTaskResponse(
    val id: Long,
    val taskNo: String,
    val status: TaskStatus,
    val assessmentUrl: String,
    val deadline: Instant,
)

data class SendAssessmentTaskResponse(
    val id: Long,
    val taskNo: String,
    val status: TaskStatus,
    val sentAt: Instant,
)

data class RegenerateAssessmentLinkResponse(
    val id: Long,
    val taskNo: String,
    val status: TaskStatus,
    val assessmentUrl: String,
    val regeneratedAt: Instant,
)
