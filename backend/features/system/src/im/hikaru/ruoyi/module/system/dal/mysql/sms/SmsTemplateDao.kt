package im.hikaru.ruoyi.module.system.dal.mysql.sms

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.template.SmsTemplatePageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsTemplateDO
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

object SmsTemplateDao {
    fun selectById(id: Long): SmsTemplateDO? = transaction {
        SmsTemplateTable.selectAll().where { conditions(SmsTemplateTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<SmsTemplateDO> = if (ids.isEmpty()) emptyList() else transaction {
        SmsTemplateTable.selectAll().where { conditions(SmsTemplateTable.id inList ids) }.map(::toEntity)
    }

    fun selectByCode(code: String): SmsTemplateDO? = transaction {
        SmsTemplateTable.selectAll().where { conditions(SmsTemplateTable.code eq code) }.singleOrNull()?.let(::toEntity)
    }

    fun selectPage(req: SmsTemplatePageReqVO): PageResult<SmsTemplateDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.type?.let { ops += SmsTemplateTable.type eq it }
        req.status?.let { ops += SmsTemplateTable.status eq it }
        req.code?.takeIf(String::isNotBlank)?.let { ops += SmsTemplateTable.code like "%$it%" }
        req.content?.takeIf(String::isNotBlank)?.let { ops += SmsTemplateTable.content like "%$it%" }
        req.apiTemplateId?.takeIf(String::isNotBlank)?.let { ops += SmsTemplateTable.apiTemplateId like "%$it%" }
        req.channelId?.let { ops += SmsTemplateTable.channelId eq it }
        req.createTime?.getOrNull(0)?.let { ops += SmsTemplateTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += SmsTemplateTable.createTime lessEq it }
        SmsTemplateTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(SmsTemplateTable.id, SortOrder.DESC).toPageResult(req, ::toEntity)
    }

    fun selectCountByChannelId(channelId: Long): Long = transaction {
        SmsTemplateTable.selectAll().where { conditions(SmsTemplateTable.channelId eq channelId) }.count()
    }

    fun selectListByStatus(status: Int): List<SmsTemplateDO> = transaction {
        SmsTemplateTable.selectAll().where { conditions(SmsTemplateTable.status eq status) }.map(::toEntity)
    }

    fun insert(entity: SmsTemplateDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            SmsTemplateTable.insert {
                it[type] = requireNotNull(entity.type)
                it[status] = requireNotNull(entity.status)
                it[code] = requireNotNull(entity.code)
                it[name] = requireNotNull(entity.name)
                it[content] = requireNotNull(entity.content)
                it[params] = entity.params.orEmpty()
                it[remark] = entity.remark
                it[apiTemplateId] = requireNotNull(entity.apiTemplateId)
                it[channelId] = requireNotNull(entity.channelId)
                it[channelCode] = requireNotNull(entity.channelCode)
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(SmsTemplateTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: SmsTemplateDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            SmsTemplateTable.update(where = { conditions(SmsTemplateTable.id eq requireNotNull(entity.id)) }) {
                entity.type?.let { value -> it[type] = value }
                entity.status?.let { value -> it[status] = value }
                entity.code?.let { value -> it[code] = value }
                entity.name?.let { value -> it[name] = value }
                entity.content?.let { value -> it[content] = value }
                entity.params?.let { value -> it[params] = value }
                it[remark] = entity.remark
                entity.apiTemplateId?.let { value -> it[apiTemplateId] = value }
                entity.channelId?.let { value -> it[channelId] = value }
                entity.channelCode?.let { value -> it[channelCode] = value }
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    fun deleteById(id: Long): Int = transaction { SmsTemplateTable.deleteWhere { SmsTemplateTable.id eq id } }

    fun deleteByIds(ids: Collection<Long>): Int = if (ids.isEmpty()) 0 else transaction {
        SmsTemplateTable.deleteWhere { SmsTemplateTable.id inList ids }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        (listOf(SmsTemplateTable.deleted eq false) + extra).compoundAnd()

    private fun toEntity(row: ResultRow) = SmsTemplateDO().apply {
        id = row[SmsTemplateTable.id]
        type = row[SmsTemplateTable.type]
        status = row[SmsTemplateTable.status]
        code = row[SmsTemplateTable.code]
        name = row[SmsTemplateTable.name]
        content = row[SmsTemplateTable.content]
        params = row[SmsTemplateTable.params]
        remark = row[SmsTemplateTable.remark]
        apiTemplateId = row[SmsTemplateTable.apiTemplateId]
        channelId = row[SmsTemplateTable.channelId]
        channelCode = row[SmsTemplateTable.channelCode]
        creator = row[SmsTemplateTable.creator]
        createTime = row[SmsTemplateTable.createTime]
        updater = row[SmsTemplateTable.updater]
        updateTime = row[SmsTemplateTable.updateTime]
        deleted = row[SmsTemplateTable.deleted]
    }
}
