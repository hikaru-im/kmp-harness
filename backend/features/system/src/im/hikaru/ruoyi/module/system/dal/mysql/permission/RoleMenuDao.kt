package im.hikaru.ruoyi.module.system.dal.mysql.permission

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleMenuDO
import org.jetbrains.exposed.v1.core.*
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object RoleMenuDao {
    fun selectListByRoleIds(roleIds: Collection<Long>): List<RoleMenuDO> = if (roleIds.isEmpty()) emptyList() else transaction { RoleMenuTable.selectAll().where { conditions(RoleMenuTable.roleId inList roleIds) }.map(::toEntity) }
    fun selectListByMenuId(menuId: Long): List<RoleMenuDO> = transaction { RoleMenuTable.selectAll().where { conditions(RoleMenuTable.menuId eq menuId) }.map(::toEntity) }
    fun insert(entity: RoleMenuDO): Long { DefaultDBFieldHandler.fillOnInsert(entity); val id = transaction { RoleMenuTable.insert {
        it[RoleMenuTable.roleId] = requireNotNull(entity.roleId); it[RoleMenuTable.menuId] = requireNotNull(entity.menuId); it[RoleMenuTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
        it[RoleMenuTable.creator] = entity.creator.orEmpty(); it[RoleMenuTable.updater] = entity.updater.orEmpty(); it[RoleMenuTable.createTime] = requireNotNull(entity.createTime); it[RoleMenuTable.updateTime] = requireNotNull(entity.updateTime)
    }.get(RoleMenuTable.id) }; entity.id = id; return id }
    fun deleteByRoleIdAndMenuIds(roleId: Long, menuIds: Collection<Long>): Int = if (menuIds.isEmpty()) 0 else transaction { RoleMenuTable.deleteWhere { conditions(RoleMenuTable.roleId eq roleId, RoleMenuTable.menuId inList menuIds) } }
    fun deleteByMenuId(menuId: Long): Int = transaction { RoleMenuTable.deleteWhere { conditions(RoleMenuTable.menuId eq menuId) } }
    fun deleteByRoleId(roleId: Long): Int = transaction { RoleMenuTable.deleteWhere { conditions(RoleMenuTable.roleId eq roleId) } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> { val ops = mutableListOf<Op<Boolean>>(RoleMenuTable.deleted eq false); if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += RoleMenuTable.tenantId eq it }; ops += extra; return ops.compoundAnd() }
    private fun toEntity(row: ResultRow) = RoleMenuDO().apply { id = row[RoleMenuTable.id]; roleId = row[RoleMenuTable.roleId]; menuId = row[RoleMenuTable.menuId]; creator = row[RoleMenuTable.creator]; createTime = row[RoleMenuTable.createTime]; updater = row[RoleMenuTable.updater]; updateTime = row[RoleMenuTable.updateTime]; deleted = row[RoleMenuTable.deleted]; tenantId = row[RoleMenuTable.tenantId] }
}
