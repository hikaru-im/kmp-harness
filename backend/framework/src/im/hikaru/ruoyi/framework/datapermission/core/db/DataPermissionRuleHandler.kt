package im.hikaru.ruoyi.framework.datapermission.core.db

import im.hikaru.ruoyi.framework.datapermission.core.rule.DataPermissionRule
import im.hikaru.ruoyi.framework.datapermission.core.rule.DataPermissionRuleFactory
import im.hikaru.ruoyi.framework.mybatis.core.mapper.ExposedDataAccessPolicy
import im.hikaru.ruoyi.framework.mybatis.core.mapper.ExposedDataAccessPolicyRegistry
import im.hikaru.ruoyi.framework.mybatis.core.mapper.ExposedDataOperation
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils.skipPermissionCheck
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.compoundAnd

/**
 * 基于 [DataPermissionRule] 的数据权限处理器
 *
 * @author 芋道源码
 */
class DataPermissionRuleHandler(
    private val ruleFactory: DataPermissionRuleFactory,
) : ExposedDataAccessPolicy, AutoCloseable {

    private val registration = ExposedDataAccessPolicyRegistry.register(this)

    /**
     * 生成 Exposed 语句需要追加的数据权限条件。
     */
    fun getExpression(table: Table, mappedStatementId: String? = null): Op<Boolean>? {
        if (skipPermissionCheck()) {
            return null
        }

        val rules = ruleFactory.getDataPermissionRule(mappedStatementId)
        if (rules.isEmpty()) {
            return null
        }

        val tableName = (table as? Alias<*>)?.delegate?.tableName ?: table.tableName
        val expressions = rules
            .filter { tableName in it.getTableNames() }
            .mapNotNull { it.getExpression(table) }
        return expressions.takeIf { it.isNotEmpty() }?.compoundAnd()
    }

    override fun getCondition(table: Table, operation: ExposedDataOperation): Op<Boolean>? = getExpression(table)

    override fun close() = registration.close()
}
