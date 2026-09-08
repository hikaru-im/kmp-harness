package im.hikaru.ruoyi.module.system.dal.mysql.tenant

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.packages.TenantPackagePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantPackageDO
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object TenantPackageDao {
    fun selectById(id: Long): TenantPackageDO? = transaction { TenantPackageTable.selectAll().where { conditions(TenantPackageTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByName(name: String): TenantPackageDO? = transaction { TenantPackageTable.selectAll().where { conditions(TenantPackageTable.name eq name) }.singleOrNull()?.let(::toEntity) }
    fun selectListByStatus(status: Int): List<TenantPackageDO> = transaction { TenantPackageTable.selectAll().where { conditions(TenantPackageTable.status eq status) }.map(::toEntity) }
    fun selectPage(req: TenantPackagePageReqVO): PageResult<TenantPackageDO> = transaction { val ops = mutableListOf<Op<Boolean>>(); req.name?.takeIf { it.isNotBlank() }?.let { ops += TenantPackageTable.name like "%$it%" }; req.status?.let { ops += TenantPackageTable.status eq it }; req.remark?.takeIf { it.isNotBlank() }?.let { ops += TenantPackageTable.remark like "%$it%" }; req.createTime?.getOrNull(0)?.let { ops += TenantPackageTable.createTime greaterEq it }; req.createTime?.getOrNull(1)?.let { ops += TenantPackageTable.createTime lessEq it }; TenantPackageTable.selectAll().where { conditions(*ops.toTypedArray()) }.orderBy(TenantPackageTable.id, SortOrder.DESC).toPageResult(req, ::toEntity) }
    fun insert(entity: TenantPackageDO): Long { DefaultDBFieldHandler.fillOnInsert(entity); val id = transaction { TenantPackageTable.insert { it[TenantPackageTable.name] = requireNotNull(entity.name); it[TenantPackageTable.status] = entity.status ?: 0; it[TenantPackageTable.remark] = entity.remark; it[TenantPackageTable.menuIds] = entity.menuIds.orEmpty(); it[TenantPackageTable.creator] = entity.creator.orEmpty(); it[TenantPackageTable.updater] = entity.updater.orEmpty(); it[TenantPackageTable.createTime] = requireNotNull(entity.createTime); it[TenantPackageTable.updateTime] = requireNotNull(entity.updateTime) }.get(TenantPackageTable.id) }; entity.id = id; return id }
    fun updateById(entity: TenantPackageDO) { DefaultDBFieldHandler.fillOnUpdate(entity); val id = requireNotNull(entity.id); transaction { TenantPackageTable.update(where = { conditions(TenantPackageTable.id eq id) }) { entity.name?.let { v -> it[TenantPackageTable.name] = v }; entity.status?.let { v -> it[TenantPackageTable.status] = v }; entity.remark?.let { v -> it[TenantPackageTable.remark] = v }; entity.menuIds?.let { v -> it[TenantPackageTable.menuIds] = v }; entity.updater?.let { v -> it[TenantPackageTable.updater] = v }; it[TenantPackageTable.updateTime] = requireNotNull(entity.updateTime) } } }
    fun deleteById(id: Long): Int = transaction { TenantPackageTable.update(where = { conditions(TenantPackageTable.id eq id) }) { it[TenantPackageTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>) = listOf(TenantPackageTable.deleted eq false, *extra).compoundAnd()
    private fun toEntity(row: ResultRow) = TenantPackageDO().apply { id = row[TenantPackageTable.id]; name = row[TenantPackageTable.name]; status = row[TenantPackageTable.status]; remark = row[TenantPackageTable.remark]; menuIds = row[TenantPackageTable.menuIds]; creator = row[TenantPackageTable.creator]; createTime = row[TenantPackageTable.createTime]; updater = row[TenantPackageTable.updater]; updateTime = row[TenantPackageTable.updateTime]; deleted = row[TenantPackageTable.deleted] }
}
