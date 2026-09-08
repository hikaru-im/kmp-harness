package im.hikaru.ruoyi.module.system.dal.mysql.mail

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.mail.vo.log.MailLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.mail.MailLogDO
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object MailLogDao {
    fun selectById(id: Long): MailLogDO? = transaction {
        MailLogTable.selectAll().where { conditions(MailLogTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectPage(req: MailLogPageReqVO): PageResult<MailLogDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.userId?.let { ops += MailLogTable.userId eq it }
        req.userType?.let { ops += MailLogTable.userType eq it }
        req.accountId?.let { ops += MailLogTable.accountId eq it }
        req.templateId?.let { ops += MailLogTable.templateId eq it }
        req.sendStatus?.let { ops += MailLogTable.sendStatus eq it }
        req.sendTime?.getOrNull(0)?.let { ops += MailLogTable.sendTime greaterEq it }
        req.sendTime?.getOrNull(1)?.let { ops += MailLogTable.sendTime lessEq it }
        req.toMail?.takeIf { it.isNotBlank() }?.let { mail ->
            ops += (MailLogTable.toMails eq mail) or
                (MailLogTable.toMails like "$mail,%") or
                (MailLogTable.toMails like "%,$mail") or
                (MailLogTable.toMails like "%,$mail,%")
        }
        MailLogTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(MailLogTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: MailLogDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            MailLogTable.insert {
                it[userId] = entity.userId
                it[userType] = entity.userType
                it[toMails] = entity.toMails.orEmpty().joinToString(",")
                it[ccMails] = entity.ccMails?.joinToString(",")
                it[bccMails] = entity.bccMails?.joinToString(",")
                it[accountId] = requireNotNull(entity.accountId)
                it[fromMail] = requireNotNull(entity.fromMail)
                it[templateId] = requireNotNull(entity.templateId)
                it[templateCode] = requireNotNull(entity.templateCode)
                it[templateNickname] = entity.templateNickname
                it[templateTitle] = requireNotNull(entity.templateTitle)
                it[templateContent] = requireNotNull(entity.templateContent)
                it[templateParams] = entity.templateParams.orEmpty()
                it[sendStatus] = requireNotNull(entity.sendStatus)
                it[sendTime] = entity.sendTime
                it[sendMessageId] = entity.sendMessageId
                it[sendException] = entity.sendException
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(MailLogTable.id)
        }
        entity.id = id
        return id
    }

    fun updateSendResult(logId: Long, status: Int, messageId: String?, exception: String?): Int = transaction {
        val now = java.time.LocalDateTime.now().toKotlinLocalDateTime()
        MailLogTable.update(where = { conditions(MailLogTable.id eq logId) }) {
            it[sendStatus] = status
            it[sendTime] = now
            it[sendMessageId] = messageId
            it[sendException] = exception
            it[updateTime] = now
        }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        listOf(MailLogTable.deleted eq false, *extra).compoundAnd()

    private fun split(value: String?): List<String>? = value?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)

    private fun toEntity(row: ResultRow) = MailLogDO().apply {
        id = row[MailLogTable.id]
        userId = row[MailLogTable.userId]
        userType = row[MailLogTable.userType]
        toMails = split(row[MailLogTable.toMails]).orEmpty()
        ccMails = split(row[MailLogTable.ccMails])
        bccMails = split(row[MailLogTable.bccMails])
        accountId = row[MailLogTable.accountId]
        fromMail = row[MailLogTable.fromMail]
        templateId = row[MailLogTable.templateId]
        templateCode = row[MailLogTable.templateCode]
        templateNickname = row[MailLogTable.templateNickname]
        templateTitle = row[MailLogTable.templateTitle]
        templateContent = row[MailLogTable.templateContent]
        templateParams = row[MailLogTable.templateParams]
        sendStatus = row[MailLogTable.sendStatus]
        sendTime = row[MailLogTable.sendTime]
        sendMessageId = row[MailLogTable.sendMessageId]
        sendException = row[MailLogTable.sendException]
        creator = row[MailLogTable.creator]
        createTime = row[MailLogTable.createTime]
        updater = row[MailLogTable.updater]
        updateTime = row[MailLogTable.updateTime]
        deleted = row[MailLogTable.deleted]
    }
}
