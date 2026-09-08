package im.hikaru.ruoyi.module.member.dal.mysql.signin

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update
import im.hikaru.ruoyi.module.member.controller.admin.signin.vo.record.MemberSignInRecordPageReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.signin.MemberSignInRecordDO
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object MemberSignInRecordDao {
    fun selectById(id: Long): MemberSignInRecordDO? = transaction { MemberSignInRecordTable.selectAll().where { conditions(MemberSignInRecordTable.id eq id) }.singleOrNull()?.let(::toEntity) }
    fun selectByIds(ids: Collection<Long>): List<MemberSignInRecordDO> = if (ids.isEmpty()) emptyList() else transaction { MemberSignInRecordTable.selectAll().where { conditions(MemberSignInRecordTable.id inList ids) }.map(::toEntity) }
    fun selectList(): List<MemberSignInRecordDO> = transaction { MemberSignInRecordTable.selectAll().where { conditions() }.map(::toEntity) }
    fun selectCount(): Long = transaction { MemberSignInRecordTable.selectAll().where { conditions() }.count() }
    fun selectPage(reqVO: MemberSignInRecordPageReqVO, userIds: Set<Long>?): PageResult<MemberSignInRecordDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        userIds?.let { if (it.isEmpty()) return@transaction PageResult.empty() else ops += MemberSignInRecordTable.userId inList it }
        reqVO.userId?.let { ops += MemberSignInRecordTable.userId eq it }
        reqVO.day?.let { ops += MemberSignInRecordTable.day eq it }
        reqVO.createTime?.takeIf { it.size >= 2 }?.let {
            ops += MemberSignInRecordTable.createTime greaterEq it[0].toKotlinLocalDateTime()
            ops += MemberSignInRecordTable.createTime lessEq it[1].toKotlinLocalDateTime()
        }
        MemberSignInRecordTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MemberSignInRecordTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }
    fun selectPage(userId: Long, pageParam: PageParam): PageResult<MemberSignInRecordDO> = transaction {
        MemberSignInRecordTable.selectAll().where { conditions(MemberSignInRecordTable.userId eq userId) }
            .orderBy(MemberSignInRecordTable.id, SortOrder.DESC)
            .toPageResult(pageParam, ::toEntity)
    }
    fun selectLastByUserId(userId: Long): MemberSignInRecordDO? = transaction {
        MemberSignInRecordTable.selectAll().where { conditions(MemberSignInRecordTable.userId eq userId) }
            .orderBy(MemberSignInRecordTable.createTime, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(::toEntity)
    }
    fun selectByUserIdAndSignDate(userId: Long, signDate: LocalDate): MemberSignInRecordDO? = transaction {
        MemberSignInRecordTable.selectAll().where {
            conditions(
                MemberSignInRecordTable.userId eq userId,
                MemberSignInRecordTable.signDate eq signDate,
            )
        }.singleOrNull()?.let(::toEntity)
    }
    fun selectCountByUserId(userId: Long): Long = transaction {
        MemberSignInRecordTable.selectAll().where { conditions(MemberSignInRecordTable.userId eq userId) }.count()
    }
    fun selectListByUserId(userId: Long): List<MemberSignInRecordDO> = transaction {
        MemberSignInRecordTable.selectAll().where { conditions(MemberSignInRecordTable.userId eq userId) }.map(::toEntity)
    }
    fun insert(entity: MemberSignInRecordDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction { MemberSignInRecordTable.insert {
            it[MemberSignInRecordTable.userId] = entity.userId
            it[MemberSignInRecordTable.day] = entity.day
            it[MemberSignInRecordTable.point] = entity.point
            it[MemberSignInRecordTable.experience] = entity.experience
            it[MemberSignInRecordTable.signDate] = entity.signDate
            it[MemberSignInRecordTable.tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
            it[MemberSignInRecordTable.creator] = entity.creator
            it[MemberSignInRecordTable.updater] = entity.updater
            it[MemberSignInRecordTable.createTime] = requireNotNull(entity.createTime)
            it[MemberSignInRecordTable.updateTime] = requireNotNull(entity.updateTime)
        }.get(MemberSignInRecordTable.id) }
        entity.id = id
        return id
    }
    fun insertIfAbsent(entity: MemberSignInRecordDO): Boolean {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val statement = transaction {
            MemberSignInRecordTable.insertIgnore {
                it[MemberSignInRecordTable.userId] = entity.userId
                it[MemberSignInRecordTable.day] = entity.day
                it[MemberSignInRecordTable.point] = entity.point
                it[MemberSignInRecordTable.experience] = entity.experience
                it[MemberSignInRecordTable.signDate] = entity.signDate
                it[MemberSignInRecordTable.tenantId] = entity.tenantId
                    ?: TenantContextHolder.getTenantId() ?: 0L
                it[MemberSignInRecordTable.creator] = entity.creator
                it[MemberSignInRecordTable.updater] = entity.updater
                it[MemberSignInRecordTable.createTime] = requireNotNull(entity.createTime)
                it[MemberSignInRecordTable.updateTime] = requireNotNull(entity.updateTime)
            }
        }
        if (statement.insertedCount == 0) return false
        entity.id = statement[MemberSignInRecordTable.id]
        return true
    }
    fun updateById(entity: MemberSignInRecordDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        return transaction { MemberSignInRecordTable.update(where = { conditions(MemberSignInRecordTable.id eq id) }) {
            entity.userId?.let { value -> it[MemberSignInRecordTable.userId] = value }
            entity.day?.let { value -> it[MemberSignInRecordTable.day] = value }
            entity.point?.let { value -> it[MemberSignInRecordTable.point] = value }
            entity.experience?.let { value -> it[MemberSignInRecordTable.experience] = value }
            entity.updater?.let { value -> it[MemberSignInRecordTable.updater] = value }
            it[MemberSignInRecordTable.updateTime] = requireNotNull(entity.updateTime)
        } }
    }
    fun deleteById(id: Long): Int = transaction { MemberSignInRecordTable.update(where = { conditions(MemberSignInRecordTable.id eq id) }) { it[MemberSignInRecordTable.deleted] = true } }
    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction { MemberSignInRecordTable.update(where = { conditions(MemberSignInRecordTable.id inList ids) }) { it[MemberSignInRecordTable.deleted] = true } }
    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(MemberSignInRecordTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += MemberSignInRecordTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }
    internal fun toEntity(row: ResultRow) = MemberSignInRecordDO().apply {
        id = row[MemberSignInRecordTable.id]
        userId = row[MemberSignInRecordTable.userId]
        day = row[MemberSignInRecordTable.day]
        point = row[MemberSignInRecordTable.point]
        experience = row[MemberSignInRecordTable.experience]
        signDate = row[MemberSignInRecordTable.signDate]
        creator = row[MemberSignInRecordTable.creator]
        createTime = row[MemberSignInRecordTable.createTime]
        updater = row[MemberSignInRecordTable.updater]
        updateTime = row[MemberSignInRecordTable.updateTime]
        deleted = row[MemberSignInRecordTable.deleted]
        tenantId = row[MemberSignInRecordTable.tenantId]
    }
}
