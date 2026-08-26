package com.acme.assessment.admin

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AdminController(private val service: AdminService) {
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun createUser(@Valid @RequestBody request: CreateUserRequest) = service.createUser(request)

    @GetMapping("/departments")
    @PreAuthorize("isAuthenticated()")
    fun listDepartments() = service.listDepartments()

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun createDepartment(@Valid @RequestBody request: CreateDepartmentRequest) = service.createDepartment(request)

    @GetMapping("/positions")
    @PreAuthorize("isAuthenticated()")
    fun listPositions() = service.listPositions()

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    fun createPosition(@Valid @RequestBody request: CreatePositionRequest) = service.createPosition(request)

    @GetMapping("/reviewers")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun listReviewers() = service.listReviewers()

    @PostMapping("/assessment-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'REVIEWER', 'ADMIN')")
    fun createTemplate(@Valid @RequestBody request: CreateTemplateRequest) = service.createTemplate(request)

    @GetMapping("/assessment-templates/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    fun listTemplateVersions(@PathVariable id: Long) = service.listTemplateVersions(id)

    @GetMapping("/assessment-templates")
    @PreAuthorize("isAuthenticated()")
    fun listTemplates() = service.listTemplates()

    @PostMapping("/assessment-template-versions/{id}/publish")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    fun publishVersion(@PathVariable id: Long) = service.publishVersion(id)
}
