package im.hikaru.ruoyi.module.system.dal.mysql.permission

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role.RolePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object RoleDao {
    fun selectById(id: Long): RoleDO? = transaction { RoleTable.selectAll().where { conditions(RoleTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<RoleDO> = if (ids.isEmpty()) emptyList() else transaction { RoleTable.selectAll().where { conditions(RoleTable.id inList ids) }.map(::toEntity) }
    fun selectByName(name: String): RoleDO? = transaction { RoleTable.selectAll().where { conditions(RoleTable.name eq name) }.singleOrNull()?.let(::toEntity) }
    fun selectByCode(code: String): RoleDO? = transaction { RoleTable.selectAll().where { conditions(RoleTable.code eq code) }.singleOrNull()?.let(::toEntity) }
    fun selectList(statuses: Collection<Int>? = null): List<RoleDO> = transaction {
        val extra = if (statuses.isNullOrEmpty()) emptyArray() else arrayOf(RoleTable.status inList statuses)
        RoleTable.selectAll().where { conditions(*extra) }.orderBy(RoleTable.sort, SortOrder.ASC).map(::toEntity)
    }
    fun selectPage(req: RolePageReqVO): PageResult<RoleDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.name?.takeIf { it.isNotBlank() }?.let { ops += RoleTable.name like "%$it%" }; req.code?.takeIf { it.isNotBlank() }?.let { ops += RoleTable.code like "%$it%" }; req.status?.let { ops += RoleTable.status eq it }
        req.createTime?.getOrNull(0)?.let { ops += RoleTable.createTime greaterEq it }; req.createTime?.getOrNull(1)?.let { ops += RoleTable.createTime lessEq it }
        RoleTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(RoleTable.sort, SortOrder.ASC).toPageResult(req, ::toEntity)
    }
    fun insert(entity: RoleDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { RoleTable.insert {
            it[RoleTable.name] = requireNotNull(entity.name); it[RoleTable.code] = requireNotNull(entity.code); it[RoleTable.sort] = requireNotNull(entity.sort)
            it[RoleTable.dataScope] = entity.dataScope ?: 1; it[RoleTable.dataScopeDeptIds] = entity.dataScopeDeptIds.orEmpty(); it[RoleTable.status] = requireNotNull(entity.status); it[RoleTable.type] = entity.type ?: 2
            it[RoleTable.remark] = entity.remark; it[RoleTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[RoleTable.creator] = entity.creator.orEmpty(); it[RoleTable.updater] = entity.updater.orEmpty(); it[RoleTable.createTime] = requireNotNull(entity.createTime); it[RoleTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(RoleTable.id) }; entity.id = id; return id
    }
    fun updateById(entity: RoleDO) { DefaultDBFieldHandler.fillOnUpdate(entity); val id = requireNotNull(entity.id); transaction { RoleTable.update(where = { conditions(RoleTable.id eq id) }) {
        entity.name?.let { v -> it[RoleTable.name] = v }; entity.code?.let { v -> it[RoleTable.code] = v }; entity.sort?.let { v -> it[RoleTable.sort] = v }; entity.dataScope?.let { v -> it[RoleTable.dataScope] = v }
        entity.dataScopeDeptIds?.let { v -> it[RoleTable.dataScopeDeptIds] = v }; entity.status?.let { v -> it[RoleTable.status] = v }; entity.type?.let { v -> it[RoleTable.type] = v }; entity.remark?.let { v -> it[RoleTable.remark] = v }
        entity.updater?.let { v -> it[RoleTable.updater] = v }; it[RoleTable.updateTime] = requireNotNull(entity.updateTime)
    } } }
    fun deleteById(id: Long): Int = transaction { RoleTable.update(where = { conditions(RoleTable.id eq id) }) { it[RoleTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> { val ops = mutableListOf<Op<Boolean>>(RoleTable.deleted eq false); if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += RoleTable.tenantId eq it }; ops += extra; return ops.compoundAnd() }
    private fun toEntity(row: ResultRow) = RoleDO().apply { id = row[RoleTable.id]; name = row[RoleTable.name]; code = row[RoleTable.code]; sort = row[RoleTable.sort]; dataScope = row[RoleTable.dataScope]; dataScopeDeptIds = row[RoleTable.dataScopeDeptIds]; status = row[RoleTable.status]; type = row[RoleTable.type]; remark = row[RoleTable.remark]; creator = row[RoleTable.creator]; createTime = row[RoleTable.createTime]; updater = row[RoleTable.updater]; updateTime = row[RoleTable.updateTime]; deleted = row[RoleTable.deleted]; tenantId = row[RoleTable.tenantId] }
}
