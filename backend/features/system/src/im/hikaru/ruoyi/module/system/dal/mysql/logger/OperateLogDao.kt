package im.hikaru.ruoyi.module.system.dal.mysql.logger

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.api.logger.dto.OperateLogPageReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.operatelog.OperateLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.OperateLogDO
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

object OperateLogDao {
    fun selectById(id: Long): OperateLogDO? = transaction {
        OperateLogTable.selectAll()
            .where { conditions(OperateLogTable.id eq id) }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectPage(req: OperateLogPageReqVO): PageResult<OperateLogDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.userId?.let { ops += OperateLogTable.userId eq it }
        req.bizId?.let { ops += OperateLogTable.bizId eq it }
        req.type?.takeIf { it.isNotBlank() }?.let { ops += OperateLogTable.type like "%$it%" }
        req.subType?.takeIf { it.isNotBlank() }?.let { ops += OperateLogTable.subType like "%$it%" }
        req.action?.takeIf { it.isNotBlank() }?.let { ops += OperateLogTable.action like "%$it%" }
        req.createTime?.getOrNull(0)?.let { ops += OperateLogTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += OperateLogTable.createTime lessEq it }
        OperateLogTable.selectAll()
            .where { conditions(*ops.toTypedArray()) }
            .orderBy(OperateLogTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun selectPage(req: OperateLogPageReqDTO): PageResult<OperateLogDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.type?.takeIf { it.isNotBlank() }?.let { ops += OperateLogTable.type eq it }
        req.bizId?.let { ops += OperateLogTable.bizId eq it }
        req.userId?.let { ops += OperateLogTable.userId eq it }
        OperateLogTable.selectAll()
            .where { conditions(*ops.toTypedArray()) }
            .orderBy(OperateLogTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: OperateLogDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            OperateLogTable.insert {
                it[traceId] = entity.traceId.orEmpty()
                it[userId] = requireNotNull(entity.userId)
                it[userType] = requireNotNull(entity.userType)
                it[type] = requireNotNull(entity.type)
                it[subType] = requireNotNull(entity.subType)
                it[bizId] = requireNotNull(entity.bizId)
                it[action] = requireNotNull(entity.action)
                it[extra] = entity.extra.orEmpty()
                it[requestMethod] = entity.requestMethod
                it[requestUrl] = entity.requestUrl
                it[userIp] = entity.userIp
                it[userAgent] = entity.userAgent
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(OperateLogTable.id)
        }
        entity.id = id
        return id
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(OperateLogTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) {
            TenantContextHolder.getTenantId()?.let { ops += OperateLogTable.tenantId eq it }
        }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = OperateLogDO().apply {
        id = row[OperateLogTable.id]
        traceId = row[OperateLogTable.traceId]
        userId = row[OperateLogTable.userId]
        userType = row[OperateLogTable.userType]
        type = row[OperateLogTable.type]
        subType = row[OperateLogTable.subType]
        bizId = row[OperateLogTable.bizId]
        action = row[OperateLogTable.action]
        extra = row[OperateLogTable.extra]
        requestMethod = row[OperateLogTable.requestMethod]
        requestUrl = row[OperateLogTable.requestUrl]
        userIp = row[OperateLogTable.userIp]
        userAgent = row[OperateLogTable.userAgent]
        tenantId = row[OperateLogTable.tenantId]
        creator = row[OperateLogTable.creator]
        createTime = row[OperateLogTable.createTime]
        updater = row[OperateLogTable.updater]
        updateTime = row[OperateLogTable.updateTime]
        deleted = row[OperateLogTable.deleted]
    }
}
