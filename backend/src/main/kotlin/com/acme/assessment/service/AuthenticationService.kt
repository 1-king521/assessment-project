package com.acme.assessment.service

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.acme.assessment.entity.UserStatus
import com.acme.assessment.repository.RoleRepository
import com.acme.assessment.repository.UserRepository
import com.acme.assessment.web.BusinessException
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
class AuthenticationService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val dingTalkUserService: DingTalkUserService,
    private val clock: Clock = Clock.systemUTC(),
) {
    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        //查看user是否存在
        val user = userRepository.findByUsername(request.username.trim())
            ?: throw invalidCredentials()
        if (user.status == UserStatus.PENDING_APPROVAL) {
            throw BusinessException("ACCOUNT_PENDING_APPROVAL", "账号正在等待管理员审核", HttpStatus.FORBIDDEN)
        }
        //查看账户是否可用
        if (user.status != UserStatus.ACTIVE || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw invalidCredentials()
        }
        //查询角色并判断是否可用
        val role = roleRepository.findById(user.roleId).orElseThrow { invalidCredentials() }
        if (role.status != "ENABLED") throw invalidCredentials()
        //获取登录时间
        user.lastLoginAt = clock.instant()
        val loginUser = LoginUser(
            id = requireNotNull(user.id),
            username = user.username,
            realName = user.realName,
            role = role.roleCode,
            departmentId = user.departmentId,
            dingtalkBound = !user.dingtalkUserId.isNullOrBlank(),
        )
        //获取jwt令牌token
        val token = jwtService.issue(loginUser)
        return LoginResponse(token.value, expiresAt = token.expiresAt, user = loginUser)
    }

    @Transactional
    fun register(request: RegisterRequest): RegistrationResponse {
        val username = request.username.trim()
        if (userRepository.existsByUsername(username)) {
            throw BusinessException("USERNAME_EXISTS", "用户名已存在", HttpStatus.CONFLICT)
        }
        val role = roleRepository.findByRoleCode(request.requestedRole)
            ?: throw BusinessException("ROLE_UNAVAILABLE", "申请的角色不可用", HttpStatus.BAD_REQUEST)
        if (role.status != "ENABLED") {
            throw BusinessException("ROLE_UNAVAILABLE", "申请的角色当前不可用", HttpStatus.BAD_REQUEST)
        }
        val now = clock.instant()
        val user = userRepository.save(User(
            username = username,
            passwordHash = passwordEncoder.encode(request.password),
            realName = request.realName.trim(),
            phone = request.phone.trim(),
            roleId = requireNotNull(role.id),
            status = UserStatus.PENDING_APPROVAL,
            dingtalkMatchStatus = "PENDING",
            createdAt = now,
            updatedAt = now,
        ))
        dingTalkUserService.resolveAndCache(user)
        return RegistrationResponse(
            id = requireNotNull(user.id), username = user.username, realName = user.realName,
            requestedRole = role.roleCode, status = user.status.name,
            dingtalkMatchStatus = user.dingtalkMatchStatus, dingtalkName = user.dingtalkName,
        )
    }

    @Transactional(readOnly = true)
    fun currentUser(): CurrentUser {
        //判断解析后的token是否有效
        val authentication = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: throw BusinessException("UNAUTHENTICATED", "用户未登录", HttpStatus.UNAUTHORIZED)
        val user = userRepository.findByUsername(authentication.name)
            ?: throw BusinessException("UNAUTHENTICATED", "登录用户不存在", HttpStatus.UNAUTHORIZED)
        if (user.status != UserStatus.ACTIVE) {
            throw BusinessException("ACCOUNT_UNAVAILABLE", "账号不可用", HttpStatus.FORBIDDEN)
        }
        val role = roleRepository.findById(user.roleId).orElseThrow {
            BusinessException("ACCOUNT_UNAVAILABLE", "用户角色不存在", HttpStatus.FORBIDDEN)
        }
        return CurrentUser(
            id = requireNotNull(user.id),
            username = user.username,
            realName = user.realName,
            role = role.roleCode,
            departmentId = user.departmentId,
            dingtalkBound = !user.dingtalkUserId.isNullOrBlank(),
        )
    }

    private fun invalidCredentials() =
        BusinessException("INVALID_CREDENTIALS", "用户名或密码错误", HttpStatus.UNAUTHORIZED)
}
