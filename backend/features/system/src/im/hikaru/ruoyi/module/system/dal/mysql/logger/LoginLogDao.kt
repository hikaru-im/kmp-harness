package im.hikaru.ruoyi.module.system.dal.mysql.logger

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.logger.LoginLogDO
import im.hikaru.ruoyi.module.system.enums.logger.LoginResultEnum
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object LoginLogDao {
    fun selectById(id: Long): LoginLogDO? = transaction {
        LoginLogTable.selectAll().where { conditions(LoginLogTable.id eq id) }.singleOrNull()?.let(::toEntity)
    }

    fun selectPage(req: LoginLogPageReqVO): PageResult<LoginLogDO> = transaction {
        val ops = mutableListOf<Op<Boolean>>()
        req.userIp?.takeIf { it.isNotBlank() }?.let { ops += LoginLogTable.userIp like "%$it%" }
        req.username?.takeIf { it.isNotBlank() }?.let { ops += LoginLogTable.username like "%$it%" }
        req.createTime?.getOrNull(0)?.let { ops += LoginLogTable.createTime greaterEq it }
        req.createTime?.getOrNull(1)?.let { ops += LoginLogTable.createTime lessEq it }
        when (req.status) {
            true -> ops += LoginLogTable.result eq LoginResultEnum.SUCCESS.result
            false -> ops += LoginLogTable.result greater LoginResultEnum.SUCCESS.result
            null -> Unit
        }
        LoginLogTable.selectAll().where { conditions(*ops.toTypedArray()) }
            .orderBy(LoginLogTable.id, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun insert(entity: LoginLogDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val id = transaction {
            LoginLogTable.insert {
                it[logType] = requireNotNull(entity.logType).toLong()
                it[traceId] = entity.traceId.orEmpty()
                it[userId] = entity.userId ?: 0L
                it[userType] = requireNotNull(entity.userType)
                it[username] = entity.username.orEmpty()
                it[result] = requireNotNull(entity.result)
                it[userIp] = requireNotNull(entity.userIp)
                it[userAgent] = entity.userAgent.orEmpty()
                it[tenantId] = entity.tenantId ?: TenantContextHolder.getTenantId() ?: 0L
                it[creator] = entity.creator.orEmpty()
                it[updater] = entity.updater.orEmpty()
                it[createTime] = requireNotNull(entity.createTime)
                it[updateTime] = requireNotNull(entity.updateTime)
            }.get(LoginLogTable.id)
        }
        entity.id = id
        return id
    }

    private fun conditions(vararg extra: Op<Boolean>): Op<Boolean> {
        val ops = mutableListOf<Op<Boolean>>(LoginLogTable.deleted eq false)
        if (!TenantContextHolder.isIgnore()) TenantContextHolder.getTenantId()?.let { ops += LoginLogTable.tenantId eq it }
        ops += extra
        return ops.compoundAnd()
    }

    private fun toEntity(row: ResultRow) = LoginLogDO().apply {
        id = row[LoginLogTable.id]
        logType = row[LoginLogTable.logType].toInt()
        traceId = row[LoginLogTable.traceId]
        userId = row[LoginLogTable.userId]
        userType = row[LoginLogTable.userType]
        username = row[LoginLogTable.username]
        result = row[LoginLogTable.result]
        userIp = row[LoginLogTable.userIp]
        userAgent = row[LoginLogTable.userAgent]
        tenantId = row[LoginLogTable.tenantId]
        creator = row[LoginLogTable.creator]
        createTime = row[LoginLogTable.createTime]
        updater = row[LoginLogTable.updater]
        updateTime = row[LoginLogTable.updateTime]
        deleted = row[LoginLogTable.deleted]
    }
}
