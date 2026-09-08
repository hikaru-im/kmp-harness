package im.hikaru.ruoyi.module.system.dal.mysql.sms

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.log.SmsLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsLogDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object SmsLogDao {
    fun selectById(id: Long): SmsLogDO? = transaction {
        SmsLogTable.selectAll().where { conditions(SmsLogTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectByApiSerialNo(apiSerialNo: String): SmsLogDO? = transaction {
        SmsLogTable.selectAll().where { conditions(SmsLogTable.apiSerialNo eq apiSerialNo) }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectPage(req: SmsLogPageReqVO): PageResult<SmsLogDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.channelId?.let { ops += SmsLogTable.channelId eq it }
        req.templateId?.let { ops += SmsLogTable.templateId eq it }
        req.mobile?.takeIf(String::isNotBlank)?.let { ops += SmsLogTable.mobile like "%$it%" }
        req.sendStatus?.let { ops += SmsLogTable.sendStatus eq it }
        req.sendTime?.getOrNull(0)?.let { ops += SmsLogTable.sendTime greaterEq it }
        req.sendTime?.getOrNull(1)?.let { ops += SmsLogTable.sendTime lessEq it }
        req.receiveStatus?.let { ops += SmsLogTable.receiveStatus eq it }
        req.receiveTime?.getOrNull(0)?.let { ops += SmsLogTable.receiveTime greaterEq it }
        req.receiveTime?.getOrNull(1)?.let { ops += SmsLogTable.receiveTime lessEq it }
        SmsLogTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(SmsLogTable.id, SortOrder.DESC).toPageResult(req, ::toEntity)
    }

    fun insert(entity: SmsLogDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            SmsLogTable.insert {
                it[channelId] = requireNotNull(entity.channelId)
                it[channelCode] = requireNotNull(entity.channelCode)
                it[templateId] = requireNotNull(entity.templateId)
                it[templateCode] = requireNotNull(entity.templateCode)
                it[templateType] = requireNotNull(entity.templateType)
                it[templateContent] = requireNotNull(entity.templateContent)
                it[templateParams] = entity.templateParams.orEmpty()
                it[apiTemplateId] = requireNotNull(entity.apiTemplateId)
                it[mobile] = requireNotNull(entity.mobile)
                it[userId] = entity.userId
                it[userType] = entity.userType
                it[sendStatus] = requireNotNull(entity.sendStatus)
                it[sendTime] = entity.sendTime
                it[apiSendCode] = entity.apiSendCode
                it[apiSendMsg] = entity.apiSendMsg
                it[apiRequestId] = entity.apiRequestId
                it[apiSerialNo] = entity.apiSerialNo
                it[receiveStatus] = requireNotNull(entity.receiveStatus)
                it[receiveTime] = entity.receiveTime
                it[apiReceiveCode] = entity.apiReceiveCode
                it[apiReceiveMsg] = entity.apiReceiveMsg
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(SmsLogTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: SmsLogDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            SmsLogTable.update(where = { conditions(SmsLogTable.id eq requireNotNull(entity.id)) }) {
                entity.sendStatus?.let { value -> it[sendStatus] = value }
                it[sendTime] = entity.sendTime
                it[apiSendCode] = entity.apiSendCode
                it[apiSendMsg] = entity.apiSendMsg
                it[apiRequestId] = entity.apiRequestId
                it[apiSerialNo] = entity.apiSerialNo
                entity.receiveStatus?.let { value -> it[receiveStatus] = value }
                it[receiveTime] = entity.receiveTime
                it[apiReceiveCode] = entity.apiReceiveCode
                it[apiReceiveMsg] = entity.apiReceiveMsg
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        (listOf(SmsLogTable.deleted eq false) + extra).compoundAnd()

    private fun toEntity(row: ResultRow) = SmsLogDO().apply {
        id = row[SmsLogTable.id]
        channelId = row[SmsLogTable.channelId]
        channelCode = row[SmsLogTable.channelCode]
        templateId = row[SmsLogTable.templateId]
        templateCode = row[SmsLogTable.templateCode]
        templateType = row[SmsLogTable.templateType]
        templateContent = row[SmsLogTable.templateContent]
        templateParams = row[SmsLogTable.templateParams]
        apiTemplateId = row[SmsLogTable.apiTemplateId]
        mobile = row[SmsLogTable.mobile]
        userId = row[SmsLogTable.userId]
        userType = row[SmsLogTable.userType]
        sendStatus = row[SmsLogTable.sendStatus]
        sendTime = row[SmsLogTable.sendTime]
        apiSendCode = row[SmsLogTable.apiSendCode]
        apiSendMsg = row[SmsLogTable.apiSendMsg]
        apiRequestId = row[SmsLogTable.apiRequestId]
        apiSerialNo = row[SmsLogTable.apiSerialNo]
        receiveStatus = row[SmsLogTable.receiveStatus]
        receiveTime = row[SmsLogTable.receiveTime]
        apiReceiveCode = row[SmsLogTable.apiReceiveCode]
        apiReceiveMsg = row[SmsLogTable.apiReceiveMsg]
        creator = row[SmsLogTable.creator]
        createTime = row[SmsLogTable.createTime]
        updater = row[SmsLogTable.updater]
        updateTime = row[SmsLogTable.updateTime]
        deleted = row[SmsLogTable.deleted]
    }
}
