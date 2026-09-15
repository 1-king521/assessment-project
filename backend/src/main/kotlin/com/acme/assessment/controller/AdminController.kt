package com.acme.assessment.controller

import com.acme.assessment.controller.*
import com.acme.assessment.service.*
import com.acme.assessment.dto.*
import com.acme.assessment.entity.*

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AdminController(private val service: AdminService) {
    @GetMapping("/users/pending")
    @PreAuthorize("hasRole('ADMIN')")
    fun listPendingUsers() = service.listPendingUsers()

    @PostMapping("/users/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    fun approveUser(@PathVariable id: Long) = service.approveUser(id)

    @PostMapping("/users/{id}/dingtalk-match")
    @PreAuthorize("hasRole('ADMIN')")
    fun matchDingtalkUser(@PathVariable id: Long) = service.matchDingtalkUser(id)

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
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun createPosition(@Valid @RequestBody request: CreatePositionRequest) = service.createPosition(request)

    @PutMapping("/positions/{id}")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun updatePosition(@PathVariable id: Long, @Valid @RequestBody request: UpdatePositionRequest) =
        service.updatePosition(id, request)

    @PutMapping("/positions/{id}/status")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun updatePositionStatus(@PathVariable id: Long, @Valid @RequestBody request: UpdatePositionStatusRequest) =
        service.updatePositionStatus(id, request)

    @DeleteMapping("/positions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun deletePosition(@PathVariable id: Long) = service.deletePosition(id)

    @GetMapping("/reviewers")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun listReviewers(@RequestParam(required = false) positionId: Long?) = service.listReviewers(positionId)

    @GetMapping("/reviewers/grouped")
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
    fun listReviewersGroupedByDepartment() = service.listReviewersGroupedByDepartment()

    @PostMapping("/assessment-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'REVIEWER', 'ADMIN')")
    fun createTemplate(@Valid @RequestBody request: CreateTemplateRequest) = service.createTemplate(request)

    @GetMapping("/assessment-templates/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    fun listTemplateVersions(@PathVariable id: Long) = service.listTemplateVersions(id)

    @PostMapping("/assessment-templates/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'REVIEWER', 'ADMIN')")
    fun createTemplateVersion(@PathVariable id: Long, @Valid @RequestBody request: CreateTemplateVersionRequest) = service.createTemplateVersion(id, request)

    @GetMapping("/assessment-templates")
    @PreAuthorize("isAuthenticated()")
    fun listTemplates() = service.listTemplates()

    @PostMapping("/assessment-template-versions/{id}/publish")
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    fun publishVersion(@PathVariable id: Long) = service.publishVersion(id)

    @DeleteMapping("/assessment-template-versions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('HR_MANAGER', 'ADMIN')")
    fun deleteVersion(@PathVariable id: Long) = service.deleteVersion(id)
}
