package im.hikaru.ruoyi.module.system.dal.mysql.notice

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.notice.vo.NoticePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notice.NoticeDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object NoticeDao {
    fun selectById(id: Long): NoticeDO? = transaction {
        NoticeTable.selectAll().where { conditions(NoticeTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectPage(req: NoticePageReqVO): PageResult<NoticeDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.title?.takeIf { it.isNotBlank() }?.let { ops += NoticeTable.title like "%$it%" }
        req.status?.let { ops += NoticeTable.status eq it }
        NoticeTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(NoticeTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: NoticeDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            NoticeTable.insert {
                it[title] = requireNotNull(entity.title)
                it[content] = requireNotNull(entity.content)
                it[type] = requireNotNull(entity.type)
                it[status] = requireNotNull(entity.status)
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(NoticeTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: NoticeDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            NoticeTable.update(where = { conditions(NoticeTable.id eq requireNotNull(entity.id)) }) {
                it[title] = requireNotNull(entity.title)
                it[content] = requireNotNull(entity.content)
                it[type] = requireNotNull(entity.type)
                it[status] = requireNotNull(entity.status)
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        NoticeTable.update(where = { conditions(NoticeTable.id eq id) }) { it[deleted] = true }
    }

    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction {
        NoticeTable.update(where = { conditions(NoticeTable.id inList ids) }) { it[deleted] = true }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(NoticeTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += NoticeTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = NoticeDO().apply {
        id = row[NoticeTable.id]
        title = row[NoticeTable.title]
        content = row[NoticeTable.content]
        type = row[NoticeTable.type]
        status = row[NoticeTable.status]
        tenantId = row[NoticeTable.tenantId]
        creator = row[NoticeTable.creator]
        createTime = row[NoticeTable.createTime]
        updater = row[NoticeTable.updater]
        updateTime = row[NoticeTable.updateTime]
        deleted = row[NoticeTable.deleted]
    }
}
