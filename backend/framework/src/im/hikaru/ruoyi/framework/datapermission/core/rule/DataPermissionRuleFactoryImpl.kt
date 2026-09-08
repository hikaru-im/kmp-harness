package im.hikaru.ruoyi.framework.datapermission.core.rule

import im.hikaru.ruoyi.framework.datapermission.core.annotation.DataPermission
import im.hikaru.ruoyi.framework.datapermission.core.aop.DataPermissionContextHolder

/**
 * 默认的 [DataPermissionRuleFactory] 实现类
 *
 * 支持通过 [DataPermissionContextHolder] 过滤数据权限。
 *
 * 迁移说明：
 *  - Lombok `@RequiredArgsConstructor` → 主构造参数
 *  - Hutool `CollUtil.isEmpty` → `isEmpty`；`ArrayUtil.isNotEmpty` → `isNotEmpty`；`ArrayUtil.contains` → `in`
 *  - 注解规则数组类型由 `Class<? extends DataPermissionRule>` 变更为 `KClass<out DataPermissionRule>`，
 *    故 `rule.getClass()` → `rule::class`
 *  - easy-trans 的 `SimpleTransService` 引用改为按类名字符串匹配，避免对未迁移依赖的硬引用
 *
 * @author 芋道源码
 */
class DataPermissionRuleFactoryImpl(
    /** 数据权限规则数组 */
    private val rules: List<DataPermissionRule>,
) : DataPermissionRuleFactory {

    override fun getDataPermissionRules(): List<DataPermissionRule> = rules

    override fun getDataPermissionRule(mappedStatementId: String?): List<DataPermissionRule> {
        // mappedStatementId 参数，暂时没有用。以后，可以基于 mappedStatementId + DataPermission 进行缓存
        // 1.1 无数据权限
        if (rules.isEmpty()) {
            return emptyList()
        }
        // 1.2 未配置，则默认开启
        val dataPermission = DataPermissionContextHolder.get() ?: return rules
        // 1.3 已配置，但禁用
        if (!dataPermission.enable) {
            return emptyList()
        }
        // 1.4 特殊：数据翻译时，强制忽略数据权限 https://github.com/YunaiV/ruoyi-vue-pro/issues/1007
        if (isTranslateCall()) {
            return emptyList()
        }

        // 2.1 情况一：已配置，只选择部分规则
        if (dataPermission.includeRules.isNotEmpty()) {
            return rules.filter { it::class in dataPermission.includeRules } // 一般规则不会太多，所以不采用 HashSet 查询
        }
        // 2.2 已配置，只排除部分规则
        if (dataPermission.excludeRules.isNotEmpty()) {
            return rules.filter { it::class !in dataPermission.excludeRules }
        }
        // 2.3 已配置，全部规则
        return rules
    }

    /**
     * 判断是否为数据翻译 (easy-trans `@Trans`) 的调用
     *
     * 目前暂时只有这个办法，已经和 easy-trans 做过沟通。
     *
     * 迁移说明：原 Java 通过 `SimpleTransService.class.getName()` 获取类名后比对堆栈；
     * 这里改为直接使用类名字符串常量，避免对未迁移的 easy-trans 依赖产生硬引用。
     *
     * @return 是否
     */
    private fun isTranslateCall(): Boolean {
        val className = SIMPLE_TRANS_SERVICE_CLASS_NAME
        val stack = Thread.currentThread().stackTrace
        for (e in stack) {
            if (className == e.className) {
                return true
            }
        }
        return false
    }

    companion object {
        /** easy-trans 的 `SimpleTransService` 全限定类名 (用于堆栈匹配，避免硬依赖) */
        private const val SIMPLE_TRANS_SERVICE_CLASS_NAME =
            "org.dromara.trans.service.impl.SimpleTransService"
    }
}
