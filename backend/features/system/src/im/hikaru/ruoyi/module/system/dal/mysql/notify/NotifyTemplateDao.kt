package im.hikaru.ruoyi.module.system.dal.mysql.notify

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.notify.vo.template.NotifyTemplatePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.notify.NotifyTemplateDO
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
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object NotifyTemplateDao {
    fun selectById(id: Long): NotifyTemplateDO? = transaction {
        NotifyTemplateTable.selectAll().where { conditions(NotifyTemplateTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectByCode(code: String): NotifyTemplateDO? = transaction {
        NotifyTemplateTable.selectAll().where { conditions(NotifyTemplateTable.code eq code) }.singleOrNull()?.let(::toEntity)
    }

    fun selectListByStatus(status: Int): List<NotifyTemplateDO> = transaction {
        NotifyTemplateTable.selectAll().where { conditions(NotifyTemplateTable.status eq status) }
            .orderBy(NotifyTemplateTable.id, SortOrder.DESC).map(::toEntity)
    }

    fun selectPage(req: NotifyTemplatePageReqVO): PageResult<NotifyTemplateDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.code?.takeIf { it.isNotBlank() }?.let { ops += NotifyTemplateTable.code like "%$it%" }
        req.name?.takeIf { it.isNotBlank() }?.let { ops += NotifyTemplateTable.name like "%$it%" }
        req.status?.let { ops += NotifyTemplateTable.status eq it }
        req.createTime?.getOrNull(0)?.let { ops += NotifyTemplateTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += NotifyTemplateTable.createTime lessEq it }
        NotifyTemplateTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(NotifyTemplateTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: NotifyTemplateDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            NotifyTemplateTable.insert {
                it[name] = requireNotNull(entity.name)
                it[code] = requireNotNull(entity.code)
                it[type] = requireNotNull(entity.type)
                it[nickname] = requireNotNull(entity.nickname)
                it[content] = requireNotNull(entity.content)
                it[params] = entity.params
                it[status] = requireNotNull(entity.status)
                it[remark] = entity.remark
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(NotifyTemplateTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: NotifyTemplateDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            NotifyTemplateTable.update(where = { conditions(NotifyTemplateTable.id eq requireNotNull(entity.id)) }) {
                it[name] = requireNotNull(entity.name)
                it[code] = requireNotNull(entity.code)
                it[type] = requireNotNull(entity.type)
                it[nickname] = requireNotNull(entity.nickname)
                it[content] = requireNotNull(entity.content)
                it[params] = entity.params
                it[status] = requireNotNull(entity.status)
                it[remark] = entity.remark
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        NotifyTemplateTable.update(where = { conditions(NotifyTemplateTable.id eq id) }) { it[deleted] = true }
    }

    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction {
        NotifyTemplateTable.update(where = { conditions(NotifyTemplateTable.id inList ids) }) { it[deleted] = true }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        listOf(NotifyTemplateTable.deleted eq false, *extra).compoundAnd()

    private fun toEntity(row: ResultRow) = NotifyTemplateDO().apply {
        id = row[NotifyTemplateTable.id]
        name = row[NotifyTemplateTable.name]
        code = row[NotifyTemplateTable.code]
        type = row[NotifyTemplateTable.type]
        nickname = row[NotifyTemplateTable.nickname]
        content = row[NotifyTemplateTable.content]
        params = row[NotifyTemplateTable.params]
        status = row[NotifyTemplateTable.status]
        remark = row[NotifyTemplateTable.remark]
        creator = row[NotifyTemplateTable.creator]
        createTime = row[NotifyTemplateTable.createTime]
        updater = row[NotifyTemplateTable.updater]
        updateTime = row[NotifyTemplateTable.updateTime]
        deleted = row[NotifyTemplateTable.deleted]
    }
}
