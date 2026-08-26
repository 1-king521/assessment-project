package com.acme.assessment.hr

import com.acme.assessment.domain.TaskStatus
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/assessment-tasks")
@PreAuthorize("hasAnyRole('HR', 'HR_MANAGER', 'ADMIN')")
class TaskManagementController(private val service: TaskManagementService) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) status: TaskStatus?,
        @RequestParam(required = false) keyword: String?,
    ): TaskPageResponse = service.list(page, pageSize, status, keyword)

    @GetMapping("/statistics")
    fun statistics(): TaskStatisticsResponse = service.statistics()

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): TaskDetailResponse = service.detail(id)

    @PostMapping("/{id}/revoke")
    fun revoke(@PathVariable id: Long): TaskActionResponse = service.revoke(id)

    @PostMapping("/{id}/extend")
    fun extend(@PathVariable id: Long, @Valid @RequestBody request: ExtendTaskRequest): TaskActionResponse = service.extend(id, request)

    @PostMapping("/{id}/archive")
    fun archive(@PathVariable id: Long): TaskActionResponse = service.archive(id)
}
