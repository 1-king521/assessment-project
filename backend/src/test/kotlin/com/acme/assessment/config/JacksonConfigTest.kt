package com.acme.assessment.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

class JacksonConfigTest {
    @Test
    fun `customizer raises the maximum JSON string length`() {
        val builder = Jackson2ObjectMapperBuilder()
        JacksonConfig().jacksonStreamReadConstraintsCustomizer().customize(builder)

        val objectMapper: ObjectMapper = builder.build()

        assertThat(objectMapper.factory.streamReadConstraints().maxStringLength)
            .isEqualTo(50_000_000)
    }
}
