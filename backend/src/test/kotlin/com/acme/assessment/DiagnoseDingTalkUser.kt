package com.acme.assessment

import com.acme.assessment.entity.User
import com.acme.assessment.repository.UserRepository
import com.acme.assessment.service.DingTalkUserService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * 诊断工具：检查特定用户能否匹配到钉钉 userid
 *
 * 运行方式：./gradlew test --tests "DiagnoseDingTalkUser"
 */
@SpringBootTest
class DiagnoseDingTalkUser {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var dingTalkUserService: DingTalkUserService

    @Test
    fun `diagnose user 5 with phone 15294987630`() {
        val user = userRepository.findById(5L).orElse(null)
        if (user == null) {
            println("❌ 用户 ID=5 不存在")
            return
        }

        println("=".repeat(60))
        println("用户信息")
        println("=".repeat(60))
        println("ID: ${user.id}")
        println("用户名: ${user.username}")
        println("真实姓名: ${user.realName}")
        println("手机号: ${user.phone}")
        println("部门ID: ${user.departmentId}")
        println("钉钉匹配状态: ${user.dingtalkMatchStatus}")
        println("钉钉用户ID: ${user.dingtalkUserId}")
        println("钉钉姓名: ${user.dingtalkName}")
        println("上次错误: ${user.dingtalkLastError}")
        println()

        println("=".repeat(60))
        println("开始查询钉钉通讯录...")
        println("=".repeat(60))

        try {
            val userId = dingTalkUserService.resolveAndCache(user)

            println()
            println("=".repeat(60))
            println("查询结果")
            println("=".repeat(60))

            if (userId != null) {
                println("✅ 成功匹配到钉钉用户")
                println("钉钉 userid: $userId")
                println("钉钉姓名: ${user.dingtalkName}")
                println("匹配手机号: ${user.dingtalkMatchedMobile}")
            } else {
                println("❌ 未找到匹配的钉钉用户")
                println("匹配状态: ${user.dingtalkMatchStatus}")
                println("错误信息: ${user.dingtalkLastError}")
                println()
                println("可能的原因：")
                println("1. 该手机号不在钉钉企业通讯录中")
                println("2. 该手机号所属用户不在应用的可见范围内")
                println("3. 手机号格式问题（已自动归一化）")
                println("4. 钉钉应用权限不足")
            }
        } catch (e: Exception) {
            println("❌ 查询异常: ${e.message}")
            e.printStackTrace()
        }

        println("=".repeat(60))
    }

    @Test
    fun `list all users and their dingtalk match status`() {
        val users = userRepository.findAll()

        println("=".repeat(80))
        println("所有用户的钉钉匹配状态")
        println("=".repeat(80))
        println()

        println("%-5s %-15s %-15s %-15s %-15s %-30s".format(
            "ID", "用户名", "手机号", "匹配状态", "钉钉userid", "错误信息"
        ))
        println("-".repeat(80))

        for (user in users) {
            val error = user.dingtalkLastError?.take(30) ?: ""
            println("%-5s %-15s %-15s %-15s %-15s %-30s".format(
                user.id ?: "",
                user.username.take(15),
                user.phone?.take(15) ?: "",
                user.dingtalkMatchStatus.take(15),
                user.dingtalkUserId?.take(15) ?: "",
                error
            ))
        }

        println()
        println("统计信息：")
        val statusCount = users.groupingBy { it.dingtalkMatchStatus }.eachCount()
        statusCount.forEach { (status, count) ->
            println("  $status: $count")
        }

        println("=".repeat(80))
    }
}
