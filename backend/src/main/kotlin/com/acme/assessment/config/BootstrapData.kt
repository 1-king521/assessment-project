package com.acme.assessment.config

import com.acme.assessment.domain.User
import com.acme.assessment.repository.RoleRepository
import com.acme.assessment.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BootstrapData(
    private val properties: AppProperties,
    private val roleRepository: RoleRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val username = properties.bootstrap.adminUsername
        if (userRepository.existsByUsername(username)) return

        val adminRole = checkNotNull(roleRepository.findByRoleCode("ADMIN")) {
            "ADMIN role was not initialized by Flyway"
        }
        userRepository.save(
            User(
                username = username,
                passwordHash = passwordEncoder.encode(properties.bootstrap.adminPassword),
                realName = "系统管理员",
                roleId = requireNotNull(adminRole.id),
            ),
        )
        logger.warn("Initial administrator '{}' was created; change its password before deployment", username)
    }
}

