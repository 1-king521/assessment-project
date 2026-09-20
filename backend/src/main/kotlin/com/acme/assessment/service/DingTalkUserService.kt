package com.acme.assessment.service

import com.acme.assessment.config.AppProperties
import com.acme.assessment.entity.User
import com.acme.assessment.dto.DingTalkProfileResponse
import com.acme.assessment.repository.UserRepository
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.time.Instant

@Service
class DingTalkUserService(
    private val properties: AppProperties,
    private val userRepository: UserRepository,
    restClientBuilder: RestClient.Builder,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val client = restClientBuilder.clone().baseUrl(properties.dingtalk.apiBaseUrl).build()
    private val oapiClient = restClientBuilder.clone().baseUrl(properties.dingtalk.oapiBaseUrl).build()
    @Volatile private var token: CachedToken? = null

    fun lookupProfile(phone: String): DingTalkProfileResponse {
        val mobile = normalizeMobile(phone)
        if (mobile.isBlank()) return DingTalkProfileResponse(false, "手机号格式不正确")
        if (!properties.dingtalk.enabled) return DingTalkProfileResponse(false, "钉钉通知未启用，请手动填写部门和岗位")
        return try {
            val lookup = findByMobile(mobile)
            val result = lookup.result
            val userId = result?.path("userid")?.asText(null)
            if (userId.isNullOrBlank()) {
                DingTalkProfileResponse(false, lookup.detail ?: "未匹配到钉钉信息")
            } else {
                val departmentId = result.path("dept_id_list")
                    .takeIf { it.isArray && it.size() > 0 }
                    ?.get(0)?.asLong()
                val departmentName = departmentId?.let { getDepartmentName(it) }
                val positionName = result.path("title").asText(null)
                    ?.takeIf { it.isNotBlank() }

                DingTalkProfileResponse(
                    matched = true,
                    message = "已读取钉钉信息，请确认部门和岗位",
                    userId = userId,
                    name = result.path("name").asText(null),
                    departmentId = departmentId,
                    departmentName = departmentName,
                    positionName = positionName,
                )
            }
        } catch (ex: RestClientException) {
            logger.warn("DingTalk profile lookup failed for phone {}", mobile, ex)
            DingTalkProfileResponse(false, ex.message ?: "钉钉接口调用失败，请手动填写部门和岗位")
        }
    }

    fun resolveAndCache(user: User): String? {
        val mobile = normalizeMobile(user.phone)
        if (mobile.isBlank()) {
            user.dingtalkMatchStatus = "NOT_FOUND"
            user.dingtalkLastError = "用户未填写手机号"
            userRepository.save(user)
            return null
        }
        if (!properties.dingtalk.enabled) {
            user.dingtalkMatchStatus = "NOT_CONFIGURED"
            user.dingtalkLastError = "钉钉通知未启用"
            userRepository.save(user)
            return null
        }
        return try {
            val lookup = findByMobile(mobile)
            val userId = lookup.result?.path("userid")?.asText(null)
            if (userId.isNullOrBlank()) {
                user.dingtalkMatchStatus = "NOT_FOUND"
                // 保留钉钉原始 errcode/errmsg：40104 与 60121 的处理方式完全不同
                // （前者手机号不属于本企业，后者在本企业但不在应用可见范围/已离职），
                // 只写一句“未找到”会让排查时无法区分。
                user.dingtalkLastError = lookup.detail ?: "钉钉通讯录未找到手机号 $mobile"
                userRepository.save(user)
                null
            } else {
                val existing = userRepository.findByDingtalkUserId(userId)
                if (existing != null && existing.id != user.id) {
                    user.dingtalkMatchStatus = "CONFLICT"
                    user.dingtalkLastError = "该钉钉用户已绑定其他系统账号"
                    userRepository.save(user)
                    null
                } else {
                    user.dingtalkUserId = userId
                    user.dingtalkName = lookup.result?.path("name")?.asText(null)
                    user.dingtalkMatchedMobile = mobile
                    user.dingtalkMatchStatus = "MATCHED"
                    user.dingtalkMatchedAt = clock.instant()
                    user.dingtalkLastError = null
                    userRepository.save(user)
                    userId
                }
            }
        } catch (ex: RestClientException) {
            logger.warn("DingTalk user lookup failed for system user {}", user.id, ex)
            user.dingtalkMatchStatus = "FAILED"
            user.dingtalkLastError = ex.message?.take(500) ?: "钉钉接口调用失败"
            userRepository.save(user)
            null
        }
    }

    fun sendText(
        user: User,
        title: String,
        content: String,
        actionPath: String? = null,
        actionLabel: String = "立即查看",
    ) {
        val userId = user.dingtalkUserId
            // 两边都要走同一套归一化，否则库里存的 13800001111 永远等不上页面填的
            // “+86 138 0000 1111”，每条通知都会重新查一次钉钉。
            ?.takeIf { user.dingtalkMatchedMobile == normalizeMobile(user.phone) }
            ?: resolveAndCache(user)

        if (userId == null) {
            logger.error(
                "DingTalk user matching failed: userId={}, phone={}, status={}, error={}",
                user.id, user.phone, user.dingtalkMatchStatus, user.dingtalkLastError
            )
            throw RestClientException("用户未匹配到钉钉 userid")
        }
        val accessToken = accessToken()
        val message = if (actionPath == null) {
            mapOf(
                "msgKey" to "sampleText",
                "msgParam" to "{\"content\":\"${escapeJson("$title\n$content")}\"}",
            )
        } else {
            val actionUrl = "${properties.webBaseUrl.trimEnd('/')}/${actionPath.trimStart('/')}"
            val markdown = "$content\n\n[$actionLabel]($actionUrl)"
            mapOf(
                "msgKey" to "sampleMarkdown",
                "msgParam" to "{\"title\":\"${escapeJson(title)}\",\"text\":\"${escapeJson(markdown)}\"}",
            )
        }
        client.post().uri("/v1.0/robot/oToMessages/batchSend")
            .header("x-acs-dingtalk-access-token", accessToken)
            .body(mapOf(
                "robotCode" to properties.dingtalk.robotCode,
                "userIds" to listOf(userId),
            ) + message)
            .retrieve().toBodilessEntity()
    }

    private fun escapeJson(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    /**
     * 按手机号查 userId。
     *
     * 旧接口 /topapi/v2/user/getbymobile 查不到专属账号（exclusive_account=true），
     * 改为遍历所有部门查找用户。
     */
    private fun findByMobile(mobile: String): MobileLookup {
        val accessToken = accessToken()
        try {
            // 1. 获取所有部门ID（递归遍历）
            val deptIds = listAllDepartments(accessToken)
            if (deptIds.isEmpty()) {
                throw RestClientException("未获取到任何部门")
            }
            logger.info("共获取到 {} 个部门，开始遍历查找手机号 {}", deptIds.size, mobile)

            // 2. 遍历部门查找用户
            for (deptId in deptIds) {
                val users = listDepartmentUsers(accessToken, deptId)
                val user = users.find { it.path("mobile").asText("") == mobile }
                if (user != null) {
                    logger.info("在部门 {} 中找到手机号 {} 对应的用户 userid={}", deptId, mobile, user.path("userid").asText())
                    return MobileLookup(user, null)
                }
            }

            return MobileLookup(null, "钉钉通讯录中未找到手机号 $mobile")
        } catch (ex: Exception) {
            logger.error("遍历钉钉部门查找手机号 {} 时异常", mobile, ex)
            throw RestClientException("查询钉钉用户失败: ${ex.message}", ex)
        }
    }

    /**
     * 获取部门名称
     */
    private fun getDepartmentName(deptId: Long): String? {
        return try {
            val accessToken = accessToken()
            val form = LinkedMultiValueMap<String, String>().apply { add("dept_id", deptId.toString()) }
            val response = oapiClient.post()
                .uri { it.path("/topapi/v2/department/get").queryParam("access_token", accessToken).build() }
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(JsonNode::class.java)
                ?: return null

            val errcode = response.path("errcode").asText("").toIntOrNull() ?: -1
            if (errcode != 0) {
                logger.warn("获取部门 {} 详情失败，errcode={}", deptId, errcode)
                return null
            }

            response.path("result").path("name").asText(null)
        } catch (ex: Exception) {
            logger.warn("获取部门 {} 名称时异常", deptId, ex)
            null
        }
    }

    /**
     * 递归获取所有部门ID（BFS遍历）
     */
    private fun listAllDepartments(accessToken: String): List<Long> {
        val allDeptIds = mutableListOf<Long>()
        val queue = ArrayDeque<Long>()
        queue.add(1L) // 从根部门开始

        while (queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
            allDeptIds.add(parentId)

            try {
                val form = LinkedMultiValueMap<String, String>().apply { add("dept_id", parentId.toString()) }
                val response = oapiClient.post()
                    .uri { it.path("/topapi/v2/department/listsub").queryParam("access_token", accessToken).build() }
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode::class.java)
                    ?: continue

                val errcode = response.path("errcode").asText("").toIntOrNull() ?: -1
                if (errcode != 0) {
                    logger.warn("获取部门 {} 的子部门失败，errcode={}", parentId, errcode)
                    continue
                }

                // 钉钉 API v2 返回的是部门对象数组: {"result": [{"dept_id": 123, ...}]}
                val result = response.path("result")
                if (result.isArray) {
                    result.forEach { dept ->
                        val deptId = dept.path("dept_id").asLong(0)
                        if (deptId > 0) {
                            queue.add(deptId)
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.warn("获取部门 {} 的子部门时异常", parentId, ex)
            }
        }

        return allDeptIds
    }

    /**
     * 获取指定部门的用户列表
     */
    private fun listDepartmentUsers(accessToken: String, deptId: Long): List<JsonNode> {
        try {
            val form = LinkedMultiValueMap<String, String>().apply {
                add("dept_id", deptId.toString())
                add("cursor", "0")
                add("size", "100")
            }
            val response = oapiClient.post()
                .uri { it.path("/topapi/v2/user/list").queryParam("access_token", accessToken).build() }
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(JsonNode::class.java)
                ?: return emptyList()

            val errcode = response.path("errcode").asText("").toIntOrNull() ?: -1
            if (errcode != 0) {
                logger.warn("获取部门 {} 的用户列表失败，errcode={}", deptId, errcode)
                return emptyList()
            }

            val userList = response.path("result").path("list")
            return if (userList.isArray) userList.toList() else emptyList()
        } catch (ex: Exception) {
            logger.warn("获取部门 {} 的用户列表时异常", deptId, ex)
            return emptyList()
        }
    }

    /**
     * 注册接口允许 `+86 138-0000-1111`、`(138)0000 1111` 这类写法（见 RegisterRequest 的 Pattern），
     * 但 getbymobile 只认 11 位裸号码，多一个空格/加号就会返回 60121，
     * 表现成“手机号明明是对的却说用户不存在”。这里统一收敛成裸号码再查。
     */
    private fun normalizeMobile(raw: String?): String {
        val digits = raw?.filter { it.isDigit() }.orEmpty()
        return when {
            digits.length == 13 && digits.startsWith("86") -> digits.removePrefix("86")
            digits.length == 15 && digits.startsWith("0086") -> digits.removePrefix("0086")
            digits.length == 12 && digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
    }

    private data class MobileLookup(val result: JsonNode?, val detail: String?)

    private fun accessToken(): String {
        val appKey = properties.dingtalk.clientId.trim()
        val appSecret = properties.dingtalk.clientSecret.trim()
        if (appKey.isBlank() || appSecret.isBlank()) {
            throw RestClientException("钉钉未配置 client-id/client-secret，无法获取 access_token")
        }
        val now = clock.instant()
        token?.takeIf { it.expiresAt.isAfter(now.plusSeconds(30)) }?.let { return it.value }
        synchronized(this) {
            token?.takeIf { it.expiresAt.isAfter(clock.instant().plusSeconds(30)) }?.let { return it.value }
            // /v1.0/oauth2/accessToken 的入参名是 appKey/appSecret，不是 clientId/clientSecret
            // （clientId/clientSecret 只用于 /v1.0/oauth2/userAccessToken 那条用户态链路）。
            // 传错名字网关会返回 400 MissingappKey。
            val response = client.post().uri("/v1.0/oauth2/accessToken")
                .body(mapOf("appKey" to appKey, "appSecret" to appSecret))
                .retrieve().body(JsonNode::class.java)
                ?: throw RestClientException("钉钉未返回access_token")
            val value = response.path("accessToken").asText(null)
                ?: response.path("access_token").asText(null)
                ?: throw RestClientException("钉钉返回结果中没有access_token")
            token = CachedToken(value, clock.instant().plusSeconds(properties.dingtalk.accessTokenTtlSeconds))
            return value
        }
    }

    private data class CachedToken(val value: String, val expiresAt: Instant)
}
