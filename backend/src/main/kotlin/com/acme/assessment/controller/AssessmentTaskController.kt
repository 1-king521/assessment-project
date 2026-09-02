package com.acme.assessment.controller

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/assessment-tasks")
class AssessmentTaskController(private val service: AssessmentTaskService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun create(@Valid @RequestBody request: CreateAssessmentTaskRequest): CreateAssessmentTaskResponse =
        service.create(request)

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun send(@PathVariable id: Long): SendAssessmentTaskResponse = service.send(id)

    @PostMapping("/{id}/regenerate-link")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun regenerateLink(@PathVariable id: Long): RegenerateAssessmentLinkResponse = service.regenerateLink(id)
}
