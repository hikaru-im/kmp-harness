package im.hikaru.ruoyi.module.system.dal.mysql.permission

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.UserRoleDO
import org.jetbrains.exposed.v1.core.*
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object UserRoleDao {
    fun selectListByUserId(userId: Long): List<UserRoleDO> = transaction { UserRoleTable.selectAll().where { conditions(UserRoleTable.userId eq userId) }.map(::toEntity) }
    fun selectListByRoleIds(roleIds: Collection<Long>): List<UserRoleDO> = if (roleIds.isEmpty()) emptyList() else transaction { UserRoleTable.selectAll().where { conditions(UserRoleTable.roleId inList roleIds) }.map(::toEntity) }
    fun insert(entity: UserRoleDO): Long { DefaultDBFieldHandler.fillOnInsert(entity); val id = transaction { UserRoleTable.insert {
        it[UserRoleTable.userId] = requireNotNull(entity.userId); it[UserRoleTable.roleId] = requireNotNull(entity.roleId); it[UserRoleTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
        it[UserRoleTable.creator] = entity.creator.orEmpty(); it[UserRoleTable.updater] = entity.updater.orEmpty(); it[UserRoleTable.createTime] = requireNotNull(entity.createTime); it[UserRoleTable.updateTime] = requireNotNull(entity.updateTime)
    }.get(UserRoleTable.id) }; entity.id = id; return id }
    fun deleteByUserIdAndRoleIds(userId: Long, roleIds: Collection<Long>): Int = if (roleIds.isEmpty()) 0 else transaction { UserRoleTable.deleteWhere { conditions(UserRoleTable.userId eq userId, UserRoleTable.roleId inList roleIds) } }
    fun deleteByUserId(userId: Long): Int = transaction { UserRoleTable.deleteWhere { conditions(UserRoleTable.userId eq userId) } }
    fun deleteByRoleId(roleId: Long): Int = transaction { UserRoleTable.deleteWhere { conditions(UserRoleTable.roleId eq roleId) } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> { val ops = mutableListOf<Op<Boolean>>(UserRoleTable.deleted eq false); if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += UserRoleTable.tenantId eq it }; ops += extra; return ops.compoundAnd() }
    private fun toEntity(row: ResultRow) = UserRoleDO().apply { id = row[UserRoleTable.id]; userId = row[UserRoleTable.userId]; roleId = row[UserRoleTable.roleId]; creator = row[UserRoleTable.creator]; createTime = row[UserRoleTable.createTime]; updater = row[UserRoleTable.updater]; updateTime = row[UserRoleTable.updateTime]; deleted = row[UserRoleTable.deleted]; tenantId = row[UserRoleTable.tenantId] }
}
