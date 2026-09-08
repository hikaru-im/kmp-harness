package im.hikaru.ruoyi.module.system.dal.mysql.dict

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.type.DictTypePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictTypeDO
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object DictTypeDao {
    fun selectById(id: Long): DictTypeDO? = transaction {
        DictTypeTable.selectAll().where { conditions(DictTypeTable.id eq id) }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<DictTypeDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            DictTypeTable.selectAll().where { conditions(DictTypeTable.id inList ids) }.map(::toEntity)
        }
    }

    fun selectPage(req: DictTypePageReqVO): PageResult<DictTypeDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>(DictTypeTable.deleted eq false)
        req.name?.takeIf { it.isNotBlank() }?.let { ops += DictTypeTable.name like "%$it%" }
        req.type?.takeIf { it.isNotBlank() }?.let { ops += DictTypeTable.type like "%$it%" }
        req.status?.let { ops += DictTypeTable.status eq it }
        req.createTime?.let { range ->
            val begin = range.getOrNull(0)?.let { kotlinx.datetime.LocalDateTime(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute, it.second) }
            val end = range.getOrNull(1)?.let { kotlinx.datetime.LocalDateTime(it.year, it.monthValue, it.dayOfMonth, it.hour, it.minute, it.second) }
            if (begin != null) ops += DictTypeTable.createTime greaterEq begin
            if (end != null) ops += DictTypeTable.createTime lessEq end
        }
        DictTypeTable.selectAll().where { ops.compoundAnd() }
            .orderBy(DictTypeTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun selectByType(type: String): DictTypeDO? = transaction {
        DictTypeTable.selectAll().where { conditions(DictTypeTable.type eq type) }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectByName(name: String): DictTypeDO? = transaction {
        DictTypeTable.selectAll().where { conditions(DictTypeTable.name eq name) }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectList(): List<DictTypeDO> = transaction {
        DictTypeTable.selectAll().where { DictTypeTable.deleted eq false }.orderBy(DictTypeTable.id, SortOrder.DESC).map(::toEntity)
    }

    fun insert(entity: DictTypeDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val name = requireNotNull(entity.name)
        val type = requireNotNull(entity.type)
        val status = requireNotNull(entity.status)
        val createTime = requireNotNull(entity.createTime)
        val updateTime = requireNotNull(entity.updateTime)
        return transaction {
            DictTypeTable.insert {
                it[DictTypeTable.name] = name
                it[DictTypeTable.type] = type
                it[DictTypeTable.status] = status
                it[DictTypeTable.remark] = entity.remark
                it[DictTypeTable.deletedTime] = entity.deletedTime
                it[DictTypeTable.creator] = entity.creator.orEmpty()
                it[DictTypeTable.updater] = entity.updater.orEmpty()
                it[DictTypeTable.createTime] = createTime
                it[DictTypeTable.updateTime] = updateTime
            }.get(DictTypeTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: DictTypeDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        val updateTime = requireNotNull(entity.updateTime)
        transaction {
            DictTypeTable.update(where = { DictTypeTable.id eq id }) {
                entity.name?.let { value -> it[DictTypeTable.name] = value }
                entity.type?.let { value -> it[DictTypeTable.type] = value }
                entity.status?.let { value -> it[DictTypeTable.status] = value }
                it[DictTypeTable.remark] = entity.remark
                entity.updater?.let { value -> it[DictTypeTable.updater] = value }
                it[DictTypeTable.updateTime] = updateTime
            }
        }
    }

    fun updateToDelete(id: Long, deletedTime: LocalDateTime) {
        transaction {
            DictTypeTable.update(where = { DictTypeTable.id eq id }) {
                it[DictTypeTable.deleted] = true
                it[DictTypeTable.deletedTime] = deletedTime
            }
        }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { DictTypeTable.deleteWhere { DictTypeTable.id inList ids } }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        listOf(DictTypeTable.deleted eq false, *extra).compoundAnd()

    private fun toEntity(row: ResultRow): DictTypeDO = DictTypeDO().apply {
        id = row[DictTypeTable.id]
        name = row[DictTypeTable.name]
        type = row[DictTypeTable.type]
        status = row[DictTypeTable.status]
        remark = row[DictTypeTable.remark]
        deletedTime = row[DictTypeTable.deletedTime]
        creator = row[DictTypeTable.creator]
        createTime = row[DictTypeTable.createTime]
        updater = row[DictTypeTable.updater]
        updateTime = row[DictTypeTable.updateTime]
        deleted = row[DictTypeTable.deleted]
    }
}
