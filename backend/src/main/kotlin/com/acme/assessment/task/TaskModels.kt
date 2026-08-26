package com.acme.assessment.task

import com.acme.assessment.domain.TaskStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Positive
import java.time.Instant

data class CreateAssessmentTaskRequest(
    @field:NotBlank(message = "候选人姓名不能为空")
    @field:Size(max = 80, message = "候选人姓名不能超过80个字符")
    val candidateName: String,
    @field:Size(max = 30, message = "手机号不能超过30个字符")
    val candidatePhone: String? = null,
    @field:Email(message = "邮箱格式不正确")
    @field:Size(max = 120, message = "邮箱不能超过120个字符")
    val candidateEmail: String? = null,
    @field:Size(max = 50, message = "候选人来源不能超过50个字符")
    val candidateSource: String? = null,
    @field:Positive(message = "岗位 ID 必须为正数")
    val positionId: Long,
    @field:Positive(message = "模板版本 ID 必须为正数")
    val templateVersionId: Long,
    @field:NotEmpty(message = "至少选择一名评估人员")
    val reviewerUserIds: Set<@Positive(message = "评估人员 ID 必须为正数") Long>,
    @field:Future(message = "截止时间必须晚于当前时间")
    val deadline: Instant,
)

data class CreateAssessmentTaskResponse(
    val id: Long,
    val taskNo: String,
    val status: TaskStatus,
    val assessmentUrl: String,
    val deadline: Instant,
    val reviewerCount: Int,
)

data class SendAssessmentTaskResponse(
    val id: Long,
    val taskNo: String,
    val status: TaskStatus,
    val sentAt: Instant,
)
