package com.acme.assessment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableMethodSecurity
class AssessmentApplication

fun main(args: Array<String>) {
    runApplication<AssessmentApplication>(*args)
}

