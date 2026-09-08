package im.hikaru.ruoyi.framework.datapermission.core.rule.dept

/**
 * [DeptDataPermissionRule] 的自定义配置接口
 *
 * @author 芋道源码
 */
fun interface DeptDataPermissionRuleCustomizer {

    /**
     * 自定义该权限规则
     *
     * 1. 调用 [DeptDataPermissionRule.addDeptColumn] 方法，配置基于 dept_id 的过滤规则
     * 2. 调用 [DeptDataPermissionRule.addUserColumn] 方法，配置基于 user_id 的过滤规则
     *
     * @param rule 权限规则
     */
    fun customize(rule: DeptDataPermissionRule)
}
