package im.hikaru.ruoyi.module.infra.dal.mysql.logger

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.logger.ApiAccessLogDO
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object ApiAccessLogDao {

    fun selectById(id: Long): ApiAccessLogDO? = transaction {
        ApiAccessLogTable.selectAll()
            .where { listOf(ApiAccessLogTable.id eq id, ApiAccessLogTable.deleted eq false).compoundAnd() }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<ApiAccessLogDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            ApiAccessLogTable.selectAll()
                .where {
                    listOf(
                        ApiAccessLogTable.id inList ids,
                        ApiAccessLogTable.deleted eq false,
                    ).compoundAnd()
                }
                .map(::toEntity)
        }
    }

    fun selectPage(reqVO: ApiAccessLogPageReqVO): PageResult<ApiAccessLogDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(ApiAccessLogTable.deleted eq false)
        reqVO.userId?.let { conditions += ApiAccessLogTable.userId eq it }
        reqVO.userType?.let { conditions += ApiAccessLogTable.userType eq it }
        reqVO.applicationName?.let { conditions += ApiAccessLogTable.applicationName eq it }
        reqVO.requestUrl?.takeIf(String::isNotEmpty)?.let {
            conditions += ApiAccessLogTable.requestUrl like "%$it%"
        }
        reqVO.beginTime?.let { (begin, end) ->
            conditions += ApiAccessLogTable.beginTime greaterEq begin.toKotlinLocalDateTime()
            conditions += ApiAccessLogTable.beginTime lessEq end.toKotlinLocalDateTime()
        }
        reqVO.duration?.let { conditions += ApiAccessLogTable.duration greaterEq it }
        reqVO.resultCode?.let { conditions += ApiAccessLogTable.resultCode eq it }
        ApiAccessLogTable.selectAll()
            .where { conditions.compoundAnd() }
            .orderBy(ApiAccessLogTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }

    fun insert(entity: ApiAccessLogDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val applicationName = requireNotNull(entity.applicationName) { "Application name must not be null" }
        val requestMethod = requireNotNull(entity.requestMethod) { "Request method must not be null" }
        val requestUrl = requireNotNull(entity.requestUrl) { "Request URL must not be null" }
        val userIp = requireNotNull(entity.userIp) { "User IP must not be null" }
        val userAgent = requireNotNull(entity.userAgent) { "User agent must not be null" }
        val beginTime = requireNotNull(entity.beginTime) { "Access log beginTime must not be null" }
        val endTime = requireNotNull(entity.endTime) { "Access log endTime must not be null" }
        val duration = requireNotNull(entity.duration) { "Access log duration must not be null" }
        val resultCode = requireNotNull(entity.resultCode) { "Access log resultCode must not be null" }
        val createTime = requireNotNull(entity.createTime) { "Access log createTime must not be null" }
        val updateTime = requireNotNull(entity.updateTime) { "Access log updateTime must not be null" }
        return transaction {
            ApiAccessLogTable.insert {
                it[ApiAccessLogTable.traceId] = entity.traceId.orEmpty()
                it[ApiAccessLogTable.userId] = entity.userId ?: 0L
                it[ApiAccessLogTable.userType] = entity.userType ?: 0
                it[ApiAccessLogTable.applicationName] = applicationName
                it[ApiAccessLogTable.requestMethod] = requestMethod
                it[ApiAccessLogTable.requestUrl] = requestUrl
                it[ApiAccessLogTable.requestParams] = entity.requestParams
                it[ApiAccessLogTable.responseBody] = entity.responseBody
                it[ApiAccessLogTable.userIp] = userIp
                it[ApiAccessLogTable.userAgent] = userAgent
                it[ApiAccessLogTable.operateModule] = entity.operateModule
                it[ApiAccessLogTable.operateName] = entity.operateName
                it[ApiAccessLogTable.operateType] = entity.operateType
                it[ApiAccessLogTable.beginTime] = beginTime
                it[ApiAccessLogTable.endTime] = endTime
                it[ApiAccessLogTable.duration] = duration
                it[ApiAccessLogTable.resultCode] = resultCode
                it[ApiAccessLogTable.resultMsg] = entity.resultMsg
                it[ApiAccessLogTable.tenantId] = TenantContextHolder.getTenantId() ?: 0L
                it[ApiAccessLogTable.creator] = entity.creator.orEmpty()
                it[ApiAccessLogTable.updater] = entity.updater.orEmpty()
                it[ApiAccessLogTable.createTime] = createTime
                it[ApiAccessLogTable.updateTime] = updateTime
            }.get(ApiAccessLogTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: ApiAccessLogDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id) { "Access log id must not be null" }
        val traceId = entity.traceId
        val userId = entity.userId
        val userType = entity.userType
        val applicationName = entity.applicationName
        val requestMethod = entity.requestMethod
        val requestUrl = entity.requestUrl
        val requestParams = entity.requestParams
        val responseBody = entity.responseBody
        val userIp = entity.userIp
        val userAgent = entity.userAgent
        val operateModule = entity.operateModule
        val operateName = entity.operateName
        val operateType = entity.operateType
        val beginTime = entity.beginTime
        val endTime = entity.endTime
        val duration = entity.duration
        val resultCode = entity.resultCode
        val resultMsg = entity.resultMsg
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            ApiAccessLogTable.update(where = { ApiAccessLogTable.id eq id }) {
                if (traceId != null) it[ApiAccessLogTable.traceId] = traceId
                if (userId != null) it[ApiAccessLogTable.userId] = userId
                if (userType != null) it[ApiAccessLogTable.userType] = userType
                if (applicationName != null) it[ApiAccessLogTable.applicationName] = applicationName
                if (requestMethod != null) it[ApiAccessLogTable.requestMethod] = requestMethod
                if (requestUrl != null) it[ApiAccessLogTable.requestUrl] = requestUrl
                if (requestParams != null) it[ApiAccessLogTable.requestParams] = requestParams
                if (responseBody != null) it[ApiAccessLogTable.responseBody] = responseBody
                if (userIp != null) it[ApiAccessLogTable.userIp] = userIp
                if (userAgent != null) it[ApiAccessLogTable.userAgent] = userAgent
                if (operateModule != null) it[ApiAccessLogTable.operateModule] = operateModule
                if (operateName != null) it[ApiAccessLogTable.operateName] = operateName
                if (operateType != null) it[ApiAccessLogTable.operateType] = operateType
                if (beginTime != null) it[ApiAccessLogTable.beginTime] = beginTime
                if (endTime != null) it[ApiAccessLogTable.endTime] = endTime
                if (duration != null) it[ApiAccessLogTable.duration] = duration
                if (resultCode != null) it[ApiAccessLogTable.resultCode] = resultCode
                if (resultMsg != null) it[ApiAccessLogTable.resultMsg] = resultMsg
                if (updater != null) it[ApiAccessLogTable.updater] = updater
                if (updateTime != null) it[ApiAccessLogTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        ApiAccessLogTable.deleteWhere { ApiAccessLogTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { ApiAccessLogTable.deleteWhere { ApiAccessLogTable.id inList ids } }
    }

    fun deleteByCreateTimeLt(createTime: LocalDateTime, limit: Int): Int {
        if (limit <= 0) return 0
        return transaction {
            val ids = ApiAccessLogTable.selectAll()
                .where { ApiAccessLogTable.createTime less createTime }
                .orderBy(ApiAccessLogTable.id, SortOrder.ASC)
                .limit(limit)
                .map { it[ApiAccessLogTable.id] }
            if (ids.isEmpty()) 0 else ApiAccessLogTable.deleteWhere { ApiAccessLogTable.id inList ids }
        }
    }

    private fun toEntity(row: ResultRow): ApiAccessLogDO = ApiAccessLogDO().apply {
        id = row[ApiAccessLogTable.id]
        traceId = row[ApiAccessLogTable.traceId]
        userId = row[ApiAccessLogTable.userId]
        userType = row[ApiAccessLogTable.userType]
        applicationName = row[ApiAccessLogTable.applicationName]
        requestMethod = row[ApiAccessLogTable.requestMethod]
        requestUrl = row[ApiAccessLogTable.requestUrl]
        requestParams = row[ApiAccessLogTable.requestParams]
        responseBody = row[ApiAccessLogTable.responseBody]
        userIp = row[ApiAccessLogTable.userIp]
        userAgent = row[ApiAccessLogTable.userAgent]
        operateModule = row[ApiAccessLogTable.operateModule]
        operateName = row[ApiAccessLogTable.operateName]
        operateType = row[ApiAccessLogTable.operateType]
        beginTime = row[ApiAccessLogTable.beginTime]
        endTime = row[ApiAccessLogTable.endTime]
        duration = row[ApiAccessLogTable.duration]
        resultCode = row[ApiAccessLogTable.resultCode]
        resultMsg = row[ApiAccessLogTable.resultMsg]
        createTime = row[ApiAccessLogTable.createTime]
        updateTime = row[ApiAccessLogTable.updateTime]
        creator = row[ApiAccessLogTable.creator]
        updater = row[ApiAccessLogTable.updater]
        deleted = row[ApiAccessLogTable.deleted]
    }
}
