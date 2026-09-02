package com.acme.assessment.config

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import com.fasterxml.jackson.core.StreamReadConstraints
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JacksonConfig {
    @Bean
    fun jacksonStreamReadConstraintsCustomizer() = Jackson2ObjectMapperBuilderCustomizer { builder ->
        builder.postConfigurer { objectMapper ->
            objectMapper.factory.setStreamReadConstraints(
                StreamReadConstraints.builder()
                    .maxStringLength(MAX_JSON_STRING_LENGTH)
                    .build()
            )
        }
    }

    private companion object {
        const val MAX_JSON_STRING_LENGTH = 50_000_000
    }
}
