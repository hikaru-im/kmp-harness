package im.hikaru.ruoyi.module.member.dal.mysql.group

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.controller.admin.group.vo.MemberGroupPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.group.MemberGroupDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberGroupDao {
    fun selectById(id: Long): MemberGroupDO? = transaction { MemberGroupTable.selectAll().where { conditions(MemberGroupTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberGroupDO> = if (ids.isEmpty()) emptyList() else transaction { MemberGroupTable.selectAll().where { conditions(MemberGroupTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberGroupDO> = transaction { MemberGroupTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberGroupTable.selectAll().where { conditions() }.count() }
    fun selectPage(reqVO: MemberGroupPageReqVO): PageResult<MemberGroupDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        reqVO.name?.takeIf { it.isNotBlank() }?.let { ops += MemberGroupTable.name like "%$it%" }
        reqVO.status?.let { ops += MemberGroupTable.status eq it }
        reqVO.createTime?.takeIf { it.size >= 2 }?.let {
            ops += MemberGroupTable.createTime greaterEq it[0].toKotlinLocalDateTime()
            ops += MemberGroupTable.createTime lessEq it[1].toKotlinLocalDateTime()
        }
        MemberGroupTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MemberGroupTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }
    fun selectListByStatus(status: Int): List<MemberGroupDO> = transaction {
        MemberGroupTable.selectAll().where { conditions(MemberGroupTable.status eq status) }.map(::toEntity)
    }
    fun insert(entity: MemberGroupDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberGroupTable.insert {
            it[MemberGroupTable.name] = entity.name
            it[MemberGroupTable.remark] = entity.remark
            it[MemberGroupTable.status] = entity.status
            it[MemberGroupTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberGroupTable.creator] = entity.creator
            it[MemberGroupTable.updater] = entity.updater
            it[MemberGroupTable.createTime] = requireNotNull(entity.createTime)
            it[MemberGroupTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberGroupTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberGroupDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberGroupTable.update(where = { conditions(MemberGroupTable.id eq id) }) {
            entity.name?.let { value -> it[MemberGroupTable.name] = value }
            entity.remark?.let { value -> it[MemberGroupTable.remark] = value }
            entity.status?.let { value -> it[MemberGroupTable.status] = value }
            entity.updater?.let { value -> it[MemberGroupTable.updater] = value }
            it[MemberGroupTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberGroupTable.update(where = { conditions(MemberGroupTable.id eq id) }) { it[MemberGroupTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberGroupTable.update(where = { conditions(MemberGroupTable.id inList ids) }) { it[MemberGroupTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberGroupTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberGroupTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberGroupDO().apply {
        id = row[MemberGroupTable.id]
        name = row[MemberGroupTable.name]
        remark = row[MemberGroupTable.remark]
        status = row[MemberGroupTable.status]
        creator = row[MemberGroupTable.creator]
        createTime = row[MemberGroupTable.createTime]
        updater = row[MemberGroupTable.updater]
        updateTime = row[MemberGroupTable.updateTime]
        deleted = row[MemberGroupTable.deleted]
        tenantId = row[MemberGroupTable.tenantId]
    }
}
