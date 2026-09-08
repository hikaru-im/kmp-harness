package im.hikaru.ruoyi.framework.datapermission.config

import im.hikaru.ruoyi.framework.common.biz.system.permission.PermissionCommonApi
import im.hikaru.ruoyi.framework.datapermission.core.rule.dept.DeptDataPermissionRule
import im.hikaru.ruoyi.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer
import im.hikaru.ruoyi.framework.security.core.LoginUser
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

/**
 * 基于部门的数据权限 AutoConfiguration
 *
 * 迁移说明：仅 Java → Kotlin，去 Lombok；`@ConditionalOnClass(LoginUser.class)` → `LoginUser::class`，
 * `value = {...}` 数组写法 → Kotlin 数组。
 *
 * @author 芋道源码
 */
@AutoConfiguration
@ConditionalOnClass(LoginUser::class)
@ConditionalOnBean(value = [DeptDataPermissionRuleCustomizer::class])
class YudaoDeptDataPermissionAutoConfiguration {

    @Bean
    fun deptDataPermissionRule(
        permissionApi: PermissionCommonApi,
        customizers: List<DeptDataPermissionRuleCustomizer>,
    ): DeptDataPermissionRule {
        // 创建 DeptDataPermissionRule 对象
        val rule = DeptDataPermissionRule(permissionApi)
        // 补全表配置
        customizers.forEach { it.customize(rule) }
        return rule
    }
}
