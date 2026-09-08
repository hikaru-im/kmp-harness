package im.hikaru.ruoyi.module.system.dal.mysql.mail

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.template.MailTemplatePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailTemplateDO
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

object MailTemplateDao {
    fun selectById(id: Long): MailTemplateDO? = transaction {
        MailTemplateTable.selectAll().where { conditions(MailTemplateTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectByCode(code: String): MailTemplateDO? = transaction {
        MailTemplateTable.selectAll().where { conditions(MailTemplateTable.code eq code) }.singleOrNull()?.let(::toEntity)
    }

    fun selectList(): List<MailTemplateDO> = transaction {
        MailTemplateTable.selectAll().where { conditions() }.orderBy(MailTemplateTable.id, SortOrder.DESC).map(::toEntity)
    }

    fun selectListByStatus(status: Int): List<MailTemplateDO> = transaction {
        MailTemplateTable.selectAll().where { conditions(MailTemplateTable.status eq status) }
            .orderBy(MailTemplateTable.id, SortOrder.DESC).map(::toEntity)
    }

    fun selectCountByAccountId(accountId: Long): Long = transaction {
        MailTemplateTable.selectAll().where { conditions(MailTemplateTable.accountId eq accountId) }.count()
    }

    fun selectPage(req: MailTemplatePageReqVO): PageResult<MailTemplateDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.status?.let { ops += MailTemplateTable.status eq it }
        req.code?.takeIf { it.isNotBlank() }?.let { ops += MailTemplateTable.code like "%$it%" }
        req.name?.takeIf { it.isNotBlank() }?.let { ops += MailTemplateTable.name like "%$it%" }
        req.accountId?.let { ops += MailTemplateTable.accountId eq it }
        req.createTime?.getOrNull(0)?.let { ops += MailTemplateTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += MailTemplateTable.createTime lessEq it }
        MailTemplateTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MailTemplateTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: MailTemplateDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            MailTemplateTable.insert {
                it[name] = requireNotNull(entity.name)
                it[code] = requireNotNull(entity.code)
                it[accountId] = requireNotNull(entity.accountId)
                it[nickname] = entity.nickname
                it[title] = requireNotNull(entity.title)
                it[content] = requireNotNull(entity.content)
                it[params] = entity.params.orEmpty()
                it[status] = requireNotNull(entity.status)
                it[remark] = entity.remark
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(MailTemplateTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: MailTemplateDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            MailTemplateTable.update(where = { conditions(MailTemplateTable.id eq requireNotNull(entity.id)) }) {
                it[name] = requireNotNull(entity.name)
                it[code] = requireNotNull(entity.code)
                it[accountId] = requireNotNull(entity.accountId)
                it[nickname] = entity.nickname
                it[title] = requireNotNull(entity.title)
                it[content] = requireNotNull(entity.content)
                it[params] = entity.params.orEmpty()
                it[status] = requireNotNull(entity.status)
                it[remark] = entity.remark
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        MailTemplateTable.update(where = { conditions(MailTemplateTable.id eq id) }) { it[deleted] = true }
    }

    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction {
        MailTemplateTable.update(where = { conditions(MailTemplateTable.id inList ids) }) { it[deleted] = true }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        listOf(MailTemplateTable.deleted eq false, *extra).compoundAnd()

    private fun toEntity(row: ResultRow) = MailTemplateDO().apply {
        id = row[MailTemplateTable.id]
        name = row[MailTemplateTable.name]
        code = row[MailTemplateTable.code]
        accountId = row[MailTemplateTable.accountId]
        nickname = row[MailTemplateTable.nickname]
        title = row[MailTemplateTable.title]
        content = row[MailTemplateTable.content]
        params = row[MailTemplateTable.params]
        status = row[MailTemplateTable.status]
        remark = row[MailTemplateTable.remark]
        creator = row[MailTemplateTable.creator]
        createTime = row[MailTemplateTable.createTime]
        updater = row[MailTemplateTable.updater]
        updateTime = row[MailTemplateTable.updateTime]
        deleted = row[MailTemplateTable.deleted]
    }
}
