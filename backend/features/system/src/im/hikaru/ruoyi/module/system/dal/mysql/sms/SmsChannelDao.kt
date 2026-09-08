package im.hikaru.ruoyi.module.system.dal.mysql.sms

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.system.controller.admin.sms.vo.channel.SmsChannelPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.sms.SmsChannelDO
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

object SmsChannelDao {
    fun selectById(id: Long): SmsChannelDO? = transaction {
        SmsChannelTable.selectAll().where { conditions(SmsChannelTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectByCode(code: String): SmsChannelDO? = transaction {
        SmsChannelTable.selectAll().where { conditions(SmsChannelTable.code eq code) }.singleOrNull()?.let(::toEntity)
    }

    fun selectList(): List<SmsChannelDO> = transaction {
        SmsChannelTable.selectAll().where { conditions() }.orderBy(SmsChannelTable.id, SortOrder.ASC).map(::toEntity)
    }

    fun selectPage(req: SmsChannelPageReqVO): PageResult<SmsChannelDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.signature?.takeIf(String::isNotBlank)?.let { ops += SmsChannelTable.signature like "%$it%" }
        req.status?.let { ops += SmsChannelTable.status eq it }
        req.createTime?.getOrNull(0)?.let { ops += SmsChannelTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += SmsChannelTable.createTime lessEq it }
        SmsChannelTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(SmsChannelTable.id, SortOrder.DESC).toPageResult(req, ::toEntity)
    }

    fun insert(entity: SmsChannelDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            SmsChannelTable.insert {
                it[signature] = requireNotNull(entity.signature)
                it[code] = requireNotNull(entity.code)
                it[status] = requireNotNull(entity.status)
                it[remark] = entity.remark
                it[apiKey] = requireNotNull(entity.apiKey)
                it[apiSecret] = entity.apiSecret
                it[callbackUrl] = entity.callbackUrl
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(SmsChannelTable.id)
        }
        entity.id = id
        return id
    }

    fun updateById(entity: SmsChannelDO): Int {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        return transaction {
            SmsChannelTable.update(where = { conditions(SmsChannelTable.id eq requireNotNull(entity.id)) }) {
                entity.signature?.let { value -> it[signature] = value }
                entity.code?.let { value -> it[code] = value }
                entity.status?.let { value -> it[status] = value }
                it[remark] = entity.remark
                entity.apiKey?.let { value -> it[apiKey] = value }
                it[apiSecret] = entity.apiSecret
                it[callbackUrl] = entity.callbackUrl
                it[updater] = entity.updater.orEmpty()
                it[updateTime] = requireNotNull(entity.updateTime)
            }
        }
    }

    fun deleteById(id: Long): Int = transaction { SmsChannelTable.deleteWhere { SmsChannelTable.id eq id } }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> =
        (listOf(SmsChannelTable.deleted eq false) + extra).compoundAnd()

    private fun toEntity(row: ResultRow) = SmsChannelDO().apply {
        id = row[SmsChannelTable.id]
        signature = row[SmsChannelTable.signature]
        code = row[SmsChannelTable.code]
        status = row[SmsChannelTable.status]
        remark = row[SmsChannelTable.remark]
        apiKey = row[SmsChannelTable.apiKey]
        apiSecret = row[SmsChannelTable.apiSecret]
        callbackUrl = row[SmsChannelTable.callbackUrl]
        creator = row[SmsChannelTable.creator]
        createTime = row[SmsChannelTable.createTime]
        updater = row[SmsChannelTable.updater]
        updateTime = row[SmsChannelTable.updateTime]
        deleted = row[SmsChannelTable.deleted]
    }
}
