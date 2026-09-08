package im.hikaru.ruoyi.module.system.framework.datapermission.config

import im.hikaru.ruoyi.framework.datapermission.core.rule.dept.DeptDataPermissionRuleCustomizer
import im.hikaru.ruoyi.module.system.dal.mysql.dept.DeptTable
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserTable
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** System module mappings used by the department data-permission rule. */
@Configuration(proxyBeanMethods = false)
class DataPermissionConfiguration {

    @Bean
    fun sysDeptDataPermissionRuleCustomizer(): DeptDataPermissionRuleCustomizer =
        DeptDataPermissionRuleCustomizer { rule ->
            rule.addDeptColumn(AdminUserTable)
            rule.addDeptColumn(DeptTable, "id")
            rule.addUserColumn(AdminUserTable, "id")
        }
}
