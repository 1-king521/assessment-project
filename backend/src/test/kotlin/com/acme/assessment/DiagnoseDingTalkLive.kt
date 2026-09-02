package com.acme.assessment

import com.acme.assessment.config.AppProperties
import com.acme.assessment.service.DingTalkUserService
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

/**
 * 实时诊断工具：直接调用钉钉 API 查看详细响应
 */
@SpringBootTest
class DiagnoseDingTalkLive {

    @Autowired
    private lateinit var properties: AppProperties

    @Autowired
    private lateinit var dingTalkUserService: DingTalkUserService

    @Test
    fun `test dingtalk api connection`() {
        println("=".repeat(80))
        println("钉钉配置信息")
        println("=".repeat(80))
        println("Enabled: ${properties.dingtalk.enabled}")
        println("Client ID: ${properties.dingtalk.clientId}")
        println("Client Secret: ${properties.dingtalk.clientSecret.take(10)}...")
        println("Robot Code: ${properties.dingtalk.robotCode}")
        println("API Base URL: ${properties.dingtalk.apiBaseUrl}")
        println("OAPI Base URL: ${properties.dingtalk.oapiBaseUrl}")
        println()

        if (properties.dingtalk.clientId.isBlank()) {
            println("❌ 错误：DINGTALK_CLIENT_ID 未配置！")
            return
        }

        val client = RestClient.builder().baseUrl(properties.dingtalk.apiBaseUrl).build()
        val oapiClient = RestClient.builder().baseUrl(properties.dingtalk.oapiBaseUrl).build()

        try {
            println("=".repeat(80))
            println("步骤 1: 获取 access_token")
            println("=".repeat(80))

            val tokenResponse = client.post()
                .uri("/v1.0/oauth2/accessToken")
                .body(mapOf(
                    "appKey" to properties.dingtalk.clientId,
                    "appSecret" to properties.dingtalk.clientSecret
                ))
                .retrieve()
                .body(JsonNode::class.java)

            println("响应: $tokenResponse")

            val accessToken = tokenResponse?.path("accessToken")?.asText()
            if (accessToken.isNullOrBlank()) {
                println("❌ 获取 access_token 失败！")
                return
            }

            println("✅ access_token: ${accessToken.take(20)}...")
            println()

            println("=".repeat(80))
            println("步骤 2: 获取部门列表")
            println("=".repeat(80))

            val deptForm = LinkedMultiValueMap<String, String>().apply { add("dept_id", "1") }
            val deptResponse = oapiClient.post()
                .uri { it.path("/topapi/v2/department/listsub").queryParam("access_token", accessToken).build() }
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(deptForm)
                .retrieve()
                .body(JsonNode::class.java)

            println("响应: $deptResponse")

            val errcode = deptResponse?.path("errcode")?.asInt(-1)
            if (errcode != 0) {
                println("❌ 获取部门列表失败，errcode=$errcode")
                println("errmsg: ${deptResponse?.path("errmsg")?.asText()}")
                return
            }

            // 从根部门开始递归获取所有子部门
            val deptIds = mutableListOf<Long>()
            val queue = ArrayDeque<Long>()
            queue.add(1L)

            while (queue.isNotEmpty()) {
                val parentId = queue.removeFirst()
                deptIds.add(parentId)

                val form = LinkedMultiValueMap<String, String>().apply { add("dept_id", parentId.toString()) }
                val response = oapiClient.post()
                    .uri { it.path("/topapi/v2/department/listsub").queryParam("access_token", accessToken).build() }
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode::class.java)

                val result = response?.path("result")
                if (result?.isArray == true) {
                    result.forEach { dept ->
                        val deptId = dept.path("dept_id").asLong(0)
                        if (deptId > 0) {
                            queue.add(deptId)
                        }
                    }
                }
            }

            println("✅ 共找到 ${deptIds.size} 个部门: $deptIds")
            println()

            println("=".repeat(80))
            println("步骤 3: 遍历部门查找手机号 15294987630")
            println("=".repeat(80))

            val targetPhone = "15294987630"
            var found = false

            for (deptId in deptIds) {
                println("\n查询部门 $deptId 的用户列表...")

                val userForm = LinkedMultiValueMap<String, String>().apply {
                    add("dept_id", deptId.toString())
                    add("cursor", "0")
                    add("size", "100")
                }

                val userResponse = oapiClient.post()
                    .uri { it.path("/topapi/v2/user/list").queryParam("access_token", accessToken).build() }
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(userForm)
                    .retrieve()
                    .body(JsonNode::class.java)

                val userErrcode = userResponse?.path("errcode")?.asInt(-1)
                if (userErrcode != 0) {
                    println("  ⚠️  获取用户列表失败，errcode=$userErrcode")
                    continue
                }

                val userList = userResponse?.path("result")?.path("list")
                if (userList?.isArray != true) {
                    println("  📭 部门为空")
                    continue
                }

                println("  📋 部门有 ${userList.size()} 个用户")

                // 打印前 3 个用户的手机号（用于对比）
                userList.take(3).forEach { user ->
                    val mobile = user.path("mobile").asText("")
                    val name = user.path("name").asText("")
                    val userid = user.path("userid").asText("")
                    println("    - $name ($userid): $mobile")
                }

                if (userList.size() > 3) {
                    println("    ... 还有 ${userList.size() - 3} 个用户")
                }

                // 查找目标手机号
                val targetUser = userList.find { it.path("mobile").asText("") == targetPhone }
                if (targetUser != null) {
                    found = true
                    println()
                    println("  ✅ 找到了！")
                    println("  userid: ${targetUser.path("userid").asText()}")
                    println("  name: ${targetUser.path("name").asText()}")
                    println("  mobile: ${targetUser.path("mobile").asText()}")
                    break
                }
            }

            println()
            println("=".repeat(80))
            if (found) {
                println("✅ 成功：手机号 $targetPhone 已找到")
            } else {
                println("❌ 未找到：手机号 $targetPhone 不在任何部门中")
                println()
                println("可能的原因：")
                println("  1. 该用户未加入钉钉企业")
                println("  2. 该用户在钉钉中使用了不同的手机号")
                println("  3. 该用户所在部门不在应用的可见范围内")
                println("  4. 手机号录入错误")
                println()
                println("建议：")
                println("  1. 登录钉钉管理后台搜索该手机号")
                println("  2. 检查应用的通讯录权限范围")
                println("  3. 核对用户在系统中填写的手机号是否正确")
            }
            println("=".repeat(80))

        } catch (e: Exception) {
            println()
            println("❌ 调用失败：${e.message}")
            e.printStackTrace()
        }
    }

    @Test
    fun `list all users in all departments`() {
        println("=".repeat(80))
        println("列出所有部门的所有用户")
        println("=".repeat(80))

        if (properties.dingtalk.clientId.isBlank()) {
            println("❌ DINGTALK_CLIENT_ID 未配置")
            return
        }

        val client = RestClient.builder().baseUrl(properties.dingtalk.apiBaseUrl).build()
        val oapiClient = RestClient.builder().baseUrl(properties.dingtalk.oapiBaseUrl).build()

        try {
            // 获取 token
            val tokenResponse = client.post()
                .uri("/v1.0/oauth2/accessToken")
                .body(mapOf(
                    "appKey" to properties.dingtalk.clientId,
                    "appSecret" to properties.dingtalk.clientSecret
                ))
                .retrieve()
                .body(JsonNode::class.java)

            val accessToken = tokenResponse?.path("accessToken")?.asText() ?: return

            // 获取所有部门
            val allDepts = mutableListOf<Long>()
            val queue = ArrayDeque<Long>()
            queue.add(1L)

            while (queue.isNotEmpty()) {
                val parentId = queue.removeFirst()
                allDepts.add(parentId)

                val deptForm = LinkedMultiValueMap<String, String>().apply { add("dept_id", parentId.toString()) }
                val deptResponse = oapiClient.post()
                    .uri { it.path("/topapi/v2/department/listsub").queryParam("access_token", accessToken).build() }
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(deptForm)
                    .retrieve()
                    .body(JsonNode::class.java)

                // 钉钉 API v2 返回: {"result": [{"dept_id": 123, ...}]}
                val result = deptResponse?.path("result")
                if (result?.isArray == true) {
                    result.forEach { dept ->
                        val deptId = dept.path("dept_id").asLong(0)
                        if (deptId > 0) {
                            queue.add(deptId)
                        }
                    }
                }
            }

            println("共找到 ${allDepts.size} 个部门")
            println()

            val allUsers = mutableListOf<Map<String, String>>()

            for (deptId in allDepts) {
                val userForm = LinkedMultiValueMap<String, String>().apply {
                    add("dept_id", deptId.toString())
                    add("cursor", "0")
                    add("size", "100")
                }

                val userResponse = oapiClient.post()
                    .uri { it.path("/topapi/v2/user/list").queryParam("access_token", accessToken).build() }
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(userForm)
                    .retrieve()
                    .body(JsonNode::class.java)

                val userList = userResponse?.path("result")?.path("list")
                if (userList?.isArray == true) {
                    userList.forEach { user ->
                        allUsers.add(mapOf(
                            "dept" to deptId.toString(),
                            "userid" to user.path("userid").asText(""),
                            "name" to user.path("name").asText(""),
                            "mobile" to user.path("mobile").asText("")
                        ))
                    }
                }
            }

            println("%-10s %-25s %-15s %-15s".format("部门ID", "userid", "姓名", "手机号"))
            println("-".repeat(80))

            allUsers.forEach { user ->
                println("%-10s %-25s %-15s %-15s".format(
                    user["dept"],
                    user["userid"]?.take(25),
                    user["name"]?.take(15),
                    user["mobile"]
                ))
            }

            println()
            println("共 ${allUsers.size} 个用户")

            // 查找目标手机号
            val target = allUsers.find { it["mobile"] == "15294987630" }
            if (target != null) {
                println()
                println("✅ 找到手机号 15294987630:")
                println("   部门: ${target["dept"]}")
                println("   userid: ${target["userid"]}")
                println("   姓名: ${target["name"]}")
            } else {
                println()
                println("❌ 手机号 15294987630 不在通讯录中")
            }

        } catch (e: Exception) {
            println("❌ 错误: ${e.message}")
            e.printStackTrace()
        }
    }
}
