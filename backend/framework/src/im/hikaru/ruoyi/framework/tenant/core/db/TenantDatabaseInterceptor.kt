package im.hikaru.ruoyi.framework.tenant.core.db

import im.hikaru.ruoyi.framework.mybatis.core.mapper.ExposedDataAccessPolicy
import im.hikaru.ruoyi.framework.mybatis.core.mapper.ExposedDataAccessPolicyRegistry
import im.hikaru.ruoyi.framework.mybatis.core.mapper.ExposedDataOperation
import im.hikaru.ruoyi.framework.tenant.config.TenantProperties
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Alias
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq

/** Adds the current tenant predicate to every policy-aware Exposed select, update, and delete. */
class TenantDatabaseInterceptor(
    properties: TenantProperties,
) : ExposedDataAccessPolicy, AutoCloseable {

    private val ignoreTables = buildSet {
        properties.ignoreTables.forEach { add(it.lowercase()) }
        add("dual")
    }

    private val registration = ExposedDataAccessPolicyRegistry.register(this)

    override fun getCondition(table: Table, operation: ExposedDataOperation): Op<Boolean>? {
        val tableName = (table as? Alias<*>)?.delegate?.tableName ?: table.tableName
        if (TenantContextHolder.isIgnore() || tableName.lowercase() in ignoreTables) {
            return null
        }
        val tenantColumn = table.columns.firstOrNull { it.name.equals(TENANT_COLUMN_NAME, ignoreCase = true) }
            ?: return null
        @Suppress("UNCHECKED_CAST")
        tenantColumn as Column<Long>
        return tenantColumn eq TenantContextHolder.getRequiredTenantId()
    }

    override fun close() = registration.close()

    private companion object {
        const val TENANT_COLUMN_NAME = "tenant_id"
    }
}
