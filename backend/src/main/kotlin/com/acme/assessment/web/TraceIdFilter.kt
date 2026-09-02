package com.acme.assessment.web

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
class TraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val traceId = request.getHeader(TRACE_HEADER)
            ?.takeIf { it.matches(TRACE_PATTERN) }
            ?: UUID.randomUUID().toString().replace("-", "")
        MDC.put(MDC_KEY, traceId)
        response.setHeader(TRACE_HEADER, traceId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    companion object {
        private const val TRACE_HEADER = "X-Trace-Id"
        private const val MDC_KEY = "traceId"
        private val TRACE_PATTERN = Regex("[A-Za-z0-9_-]{8,64}")
    }
}

