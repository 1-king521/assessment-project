package com.acme.assessment.dto

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

data class LoginRequest(
    @field:NotBlank(message = "用户名不能为空")
    val username: String,
    @field:NotBlank(message = "密码不能为空")
    val password: String,
)

data class RegisterRequest(
    @field:NotBlank @field:Size(max = 80) val username: String,
    @field:NotBlank @field:Size(min = 8, max = 128) val password: String,
    @field:NotBlank @field:Size(max = 80) val realName: String,
    @field:NotBlank @field:Pattern(regexp = "^[0-9+() -]{6,30}$", message = "手机号格式不正确") val phone: String,
    @field:NotBlank @field:Pattern(regexp = "^(HR|REVIEWER)$", message = "只能申请HR或评估人员角色") val requestedRole: String,
    @field:Size(max = 100) val departmentName: String? = null,
    @field:Size(max = 100) val positionName: String? = null,
)

data class DingTalkProfileRequest(
    @field:NotBlank @field:Pattern(regexp = "^[0-9+() -]{6,30}$", message = "手机号格式不正确") val phone: String,
)

data class DingTalkProfileResponse(
    val matched: Boolean,
    val message: String,
    val userId: String? = null,
    val name: String? = null,
    val departmentId: Long? = null,
    val departmentName: String? = null,
    val positionName: String? = null,
)

data class RegistrationResponse(
    val id: Long,
    val username: String,
    val realName: String,
    val requestedRole: String,
    val status: String,
    val dingtalkMatchStatus: String,
    val dingtalkName: String?,
)

data class LoginResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresAt: Instant,
    val user: LoginUser,
)

data class LoginUser(
    val id: Long,
    val username: String,
    val realName: String,
    val role: String,
    val departmentId: Long?,
    val dingtalkBound: Boolean = false,
)

data class CurrentUser(
    val id: Long,
    val username: String,
    val realName: String,
    val role: String,
    val departmentId: Long?,
    val dingtalkBound: Boolean = false,
) {
    fun isAnyRole(vararg allowed: String): Boolean = role in allowed
}
