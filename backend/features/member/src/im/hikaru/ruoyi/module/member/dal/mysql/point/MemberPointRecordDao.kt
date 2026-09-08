package im.hikaru.ruoyi.module.member.dal.mysql.point

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.controller.admin.point.vo.recrod.MemberPointRecordPageReqVO
import im.hikaru.ruoyi.module.member.controller.app.point.vo.AppMemberPointRecordPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.point.MemberPointRecordDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberPointRecordDao {
    fun selectById(id: Long): MemberPointRecordDO? = transaction { MemberPointRecordTable.selectAll().where { conditions(MemberPointRecordTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberPointRecordDO> = if (ids.isEmpty()) emptyList() else transaction { MemberPointRecordTable.selectAll().where { conditions(MemberPointRecordTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberPointRecordDO> = transaction { MemberPointRecordTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberPointRecordTable.selectAll().where { conditions() }.count() }
    fun selectPage(reqVO: MemberPointRecordPageReqVO, userIds: Set<Long>?): PageResult<MemberPointRecordDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        userIds?.let { if (it.isEmpty()) return@transaction PageResult.empty() else ops += MemberPointRecordTable.userId inList it }
        reqVO.userId?.let { ops += MemberPointRecordTable.userId eq it }
        reqVO.bizType?.let { ops += MemberPointRecordTable.bizType eq it }
        reqVO.title?.takeIf { it.isNotBlank() }?.let { ops += MemberPointRecordTable.title like "%$it%" }
        MemberPointRecordTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MemberPointRecordTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }
    fun selectPage(userId: Long, reqVO: AppMemberPointRecordPageReqVO): PageResult<MemberPointRecordDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>(MemberPointRecordTable.userId eq userId)
        reqVO.createTime?.takeIf { it.size >= 2 }?.let {
            ops += MemberPointRecordTable.createTime greaterEq it[0].toKotlinLocalDateTime()
            ops += MemberPointRecordTable.createTime lessEq it[1].toKotlinLocalDateTime()
        }
        when (reqVO.addStatus) {
            true -> ops += MemberPointRecordTable.point greater 0
            false -> ops += MemberPointRecordTable.point less 0
            null -> Unit
        }
        MemberPointRecordTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MemberPointRecordTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }
    fun insert(entity: MemberPointRecordDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberPointRecordTable.insert {
            it[MemberPointRecordTable.userId] = entity.userId
            it[MemberPointRecordTable.bizId] = entity.bizId
            it[MemberPointRecordTable.bizType] = entity.bizType
            it[MemberPointRecordTable.title] = entity.title
            it[MemberPointRecordTable.description] = entity.description
            it[MemberPointRecordTable.point] = entity.point
            it[MemberPointRecordTable.totalPoint] = entity.totalPoint
            it[MemberPointRecordTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberPointRecordTable.creator] = entity.creator
            it[MemberPointRecordTable.updater] = entity.updater
            it[MemberPointRecordTable.createTime] = requireNotNull(entity.createTime)
            it[MemberPointRecordTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberPointRecordTable.id) }
        entity.id = id
        return id
    }
    fun updateById(entity: MemberPointRecordDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberPointRecordTable.update(where = { conditions(MemberPointRecordTable.id eq id) }) {
            entity.userId?.let { value -> it[MemberPointRecordTable.userId] = value }
            entity.bizId?.let { value -> it[MemberPointRecordTable.bizId] = value }
            entity.bizType?.let { value -> it[MemberPointRecordTable.bizType] = value }
            entity.title?.let { value -> it[MemberPointRecordTable.title] = value }
            entity.description?.let { value -> it[MemberPointRecordTable.description] = value }
            entity.point?.let { value -> it[MemberPointRecordTable.point] = value }
            entity.totalPoint?.let { value -> it[MemberPointRecordTable.totalPoint] = value }
            entity.updater?.let { value -> it[MemberPointRecordTable.updater] = value }
            it[MemberPointRecordTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberPointRecordTable.update(where = { conditions(MemberPointRecordTable.id eq id) }) { it[MemberPointRecordTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberPointRecordTable.update(where = { conditions(MemberPointRecordTable.id inList ids) }) { it[MemberPointRecordTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberPointRecordTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberPointRecordTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberPointRecordDO().apply {
        id = row[MemberPointRecordTable.id]
        userId = row[MemberPointRecordTable.userId]
        bizId = row[MemberPointRecordTable.bizId]
        bizType = row[MemberPointRecordTable.bizType]
        title = row[MemberPointRecordTable.title]
        description = row[MemberPointRecordTable.description]
        point = row[MemberPointRecordTable.point]
        totalPoint = row[MemberPointRecordTable.totalPoint]
        creator = row[MemberPointRecordTable.creator]
        createTime = row[MemberPointRecordTable.createTime]
        updater = row[MemberPointRecordTable.updater]
        updateTime = row[MemberPointRecordTable.updateTime]
        deleted = row[MemberPointRecordTable.deleted]
        tenantId = row[MemberPointRecordTable.tenantId]
    }
}
