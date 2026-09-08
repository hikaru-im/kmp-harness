package im.hikaru.ruoyi.module.system.dal.mysql.dept

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.dept.DeptListReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.DeptDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object DeptDao {
    fun selectById(id: Long): DeptDO? = transaction { DeptTable.selectAll().where { conditions(DeptTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<DeptDO> = if (ids.isEmpty()) emptyList() else transaction { DeptTable.selectAll().where { conditions(DeptTable.id inList ids) }.map(::toEntity) }
    fun selectList(req: DeptListReqVO): List<DeptDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.name?.takeIf { it.isNotBlank() }?.let { ops += DeptTable.name like "%$it%" }
        req.status?.let { ops += DeptTable.status eq it }
        DeptTable.selectAll().where { (conditions(*ops.toTypedArray())) }.orderBy(DeptTable.sort, SortOrder.ASC).map(::toEntity)
    }
    fun selectByParentIdAndName(parentId: Long, name: String): DeptDO? = transaction { DeptTable.selectAll().where { conditions(DeptTable.parentId eq parentId, DeptTable.name eq name) }.singleOrNull()?.let(::toEntity) }
    fun selectCountByParentId(parentId: Long): Long = transaction { DeptTable.selectAll().where { conditions(DeptTable.parentId eq parentId) }.count() }
    fun selectListByParentId(parentIds: Collection<Long>): List<DeptDO> = if (parentIds.isEmpty()) emptyList() else transaction { DeptTable.selectAll().where { conditions(DeptTable.parentId inList parentIds) }.map(::toEntity) }
    fun selectListByLeaderUserId(id: Long): List<DeptDO> = transaction { DeptTable.selectAll().where { conditions(DeptTable.leaderUserId eq id) }.map(::toEntity) }

    fun insert(entity: DeptDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            DeptTable.insert {
                it[DeptTable.name] = requireNotNull(entity.name)
                it[DeptTable.parentId] = entity.parentId ?: DeptDO.PARENT_ID_ROOT
                it[DeptTable.sort] = requireNotNull(entity.sort)
                it[DeptTable.leaderUserId] = entity.leaderUserId
                it[DeptTable.phone] = entity.phone
                it[DeptTable.email] = entity.email
                it[DeptTable.status] = requireNotNull(entity.status)
                it[DeptTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[DeptTable.creator] = entity.creator.orEmpty()
                it[DeptTable.updater] = entity.updater.orEmpty()
                it[DeptTable.createTime] = requireNotNull(entity.createTime)
                it[DeptTable.updateTime] = requireNotNull(entity.updateTime)
            }.get(DeptTable.id)
        }
        entity.id = id
        return id
    }
    fun updateById(entity: DeptDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        transaction { DeptTable.update(where = { conditions(DeptTable.id eq id) }) {
            entity.name?.let { value -> it[DeptTable.name] = value }
            entity.parentId?.let { value -> it[DeptTable.parentId] = value }
            entity.sort?.let { value -> it[DeptTable.sort] = value }
            it[DeptTable.leaderUserId] = entity.leaderUserId
            it[DeptTable.phone] = entity.phone
            it[DeptTable.email] = entity.email
            entity.status?.let { value -> it[DeptTable.status] = value }
            entity.updater?.let { value -> it[DeptTable.updater] = value }
            it[DeptTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { DeptTable.update(where = { conditions(DeptTable.id eq id) }) { it[DeptTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { DeptTable.update(where = { conditions(DeptTable.id inList ids) }) { it[DeptTable.deleted] = true } }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(DeptTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += DeptTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    private fun toEntity(row: ResultRow) = DeptDO().apply {
        id = row[DeptTable.id]; name = row[DeptTable.name]; parentId = row[DeptTable.parentId]; sort = row[DeptTable.sort]
        leaderUserId = row[DeptTable.leaderUserId]; phone = row[DeptTable.phone]; email = row[DeptTable.email]; status = row[DeptTable.status]
        creator = row[DeptTable.creator]; createTime = row[DeptTable.createTime]; updater = row[DeptTable.updater]; updateTime = row[DeptTable.updateTime]
        deleted = row[DeptTable.deleted]; tenantId = row[DeptTable.tenantId]
    }
}
