package im.hikaru.ruoyi.module.infra.dal.mysql.logger

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.logger.ApiErrorLogDO
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.like
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object ApiErrorLogDao {

    fun selectById(id: Long): ApiErrorLogDO? = transaction {
        ApiErrorLogTable.selectAll()
            .where { listOf(ApiErrorLogTable.id eq id, ApiErrorLogTable.deleted eq false).compoundAnd() }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<ApiErrorLogDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            ApiErrorLogTable.selectAll()
                .where {
                    listOf(
                        ApiErrorLogTable.id inList ids,
                        ApiErrorLogTable.deleted eq false,
                    ).compoundAnd()
                }
                .map(::toEntity)
        }
    }

    fun selectPage(reqVO: ApiErrorLogPageReqVO): PageResult<ApiErrorLogDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(ApiErrorLogTable.deleted eq false)
        reqVO.userId?.let { conditions += ApiErrorLogTable.userId eq it }
        reqVO.userType?.let { conditions += ApiErrorLogTable.userType eq it }
        reqVO.applicationName?.let { conditions += ApiErrorLogTable.applicationName eq it }
        reqVO.requestUrl?.takeIf(String::isNotEmpty)?.let {
            conditions += ApiErrorLogTable.requestUrl like "%$it%"
        }
        reqVO.exceptionTime?.let { (begin, end) ->
            conditions += ApiErrorLogTable.exceptionTime greaterEq begin.toKotlinLocalDateTime()
            conditions += ApiErrorLogTable.exceptionTime lessEq end.toKotlinLocalDateTime()
        }
        reqVO.processStatus?.let { conditions += ApiErrorLogTable.processStatus eq it }
        ApiErrorLogTable.selectAll()
            .where { conditions.compoundAnd() }
            .orderBy(ApiErrorLogTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }

    fun insert(entity: ApiErrorLogDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val applicationName = requireNotNull(entity.applicationName) { "Application name must not be null" }
        val requestMethod = requireNotNull(entity.requestMethod) { "Request method must not be null" }
        val requestUrl = requireNotNull(entity.requestUrl) { "Request URL must not be null" }
        val requestParams = requireNotNull(entity.requestParams) { "Request params must not be null" }
        val userIp = requireNotNull(entity.userIp) { "User IP must not be null" }
        val userAgent = requireNotNull(entity.userAgent) { "User agent must not be null" }
        val exceptionTime = requireNotNull(entity.exceptionTime) { "Exception time must not be null" }
        val exceptionName = requireNotNull(entity.exceptionName) { "Exception name must not be null" }
        val exceptionMessage = requireNotNull(entity.exceptionMessage) { "Exception message must not be null" }
        val rootCauseMessage = requireNotNull(entity.exceptionRootCauseMessage) { "Root cause must not be null" }
        val stackTrace = requireNotNull(entity.exceptionStackTrace) { "Stack trace must not be null" }
        val className = requireNotNull(entity.exceptionClassName) { "Exception class must not be null" }
        val fileName = requireNotNull(entity.exceptionFileName) { "Exception file must not be null" }
        val methodName = requireNotNull(entity.exceptionMethodName) { "Exception method must not be null" }
        val lineNumber = requireNotNull(entity.exceptionLineNumber) { "Exception line must not be null" }
        val processStatus = requireNotNull(entity.processStatus) { "Process status must not be null" }
        val createTime = requireNotNull(entity.createTime) { "Error log createTime must not be null" }
        val updateTime = requireNotNull(entity.updateTime) { "Error log updateTime must not be null" }
        return transaction {
            ApiErrorLogTable.insert {
                it[ApiErrorLogTable.traceId] = entity.traceId.orEmpty()
                it[ApiErrorLogTable.userId] = entity.userId ?: 0L
                it[ApiErrorLogTable.userType] = entity.userType ?: 0
                it[ApiErrorLogTable.applicationName] = applicationName
                it[ApiErrorLogTable.requestMethod] = requestMethod
                it[ApiErrorLogTable.requestUrl] = requestUrl
                it[ApiErrorLogTable.requestParams] = requestParams
                it[ApiErrorLogTable.userIp] = userIp
                it[ApiErrorLogTable.userAgent] = userAgent
                it[ApiErrorLogTable.exceptionTime] = exceptionTime
                it[ApiErrorLogTable.exceptionName] = exceptionName
                it[ApiErrorLogTable.exceptionMessage] = exceptionMessage
                it[ApiErrorLogTable.exceptionRootCauseMessage] = rootCauseMessage
                it[ApiErrorLogTable.exceptionStackTrace] = stackTrace
                it[ApiErrorLogTable.exceptionClassName] = className
                it[ApiErrorLogTable.exceptionFileName] = fileName
                it[ApiErrorLogTable.exceptionMethodName] = methodName
                it[ApiErrorLogTable.exceptionLineNumber] = lineNumber
                it[ApiErrorLogTable.processStatus] = processStatus
                it[ApiErrorLogTable.processTime] = entity.processTime
                it[ApiErrorLogTable.processUserId] = entity.processUserId?.let(Math::toIntExact)
                it[ApiErrorLogTable.tenantId] = TenantContextHolder.getTenantId() ?: 0L
                it[ApiErrorLogTable.creator] = entity.creator.orEmpty()
                it[ApiErrorLogTable.updater] = entity.updater.orEmpty()
                it[ApiErrorLogTable.createTime] = createTime
                it[ApiErrorLogTable.updateTime] = updateTime
            }.get(ApiErrorLogTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: ApiErrorLogDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id) { "Error log id must not be null" }
        val traceId = entity.traceId
        val userId = entity.userId
        val userType = entity.userType
        val applicationName = entity.applicationName
        val requestMethod = entity.requestMethod
        val requestUrl = entity.requestUrl
        val requestParams = entity.requestParams
        val userIp = entity.userIp
        val userAgent = entity.userAgent
        val exceptionTime = entity.exceptionTime
        val exceptionName = entity.exceptionName
        val exceptionMessage = entity.exceptionMessage
        val rootCauseMessage = entity.exceptionRootCauseMessage
        val stackTrace = entity.exceptionStackTrace
        val className = entity.exceptionClassName
        val fileName = entity.exceptionFileName
        val methodName = entity.exceptionMethodName
        val lineNumber = entity.exceptionLineNumber
        val processStatus = entity.processStatus
        val processTime = entity.processTime
        val processUserId = entity.processUserId
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            ApiErrorLogTable.update(where = { ApiErrorLogTable.id eq id }) {
                if (traceId != null) it[ApiErrorLogTable.traceId] = traceId
                if (userId != null) it[ApiErrorLogTable.userId] = userId
                if (userType != null) it[ApiErrorLogTable.userType] = userType
                if (applicationName != null) it[ApiErrorLogTable.applicationName] = applicationName
                if (requestMethod != null) it[ApiErrorLogTable.requestMethod] = requestMethod
                if (requestUrl != null) it[ApiErrorLogTable.requestUrl] = requestUrl
                if (requestParams != null) it[ApiErrorLogTable.requestParams] = requestParams
                if (userIp != null) it[ApiErrorLogTable.userIp] = userIp
                if (userAgent != null) it[ApiErrorLogTable.userAgent] = userAgent
                if (exceptionTime != null) it[ApiErrorLogTable.exceptionTime] = exceptionTime
                if (exceptionName != null) it[ApiErrorLogTable.exceptionName] = exceptionName
                if (exceptionMessage != null) it[ApiErrorLogTable.exceptionMessage] = exceptionMessage
                if (rootCauseMessage != null) it[ApiErrorLogTable.exceptionRootCauseMessage] = rootCauseMessage
                if (stackTrace != null) it[ApiErrorLogTable.exceptionStackTrace] = stackTrace
                if (className != null) it[ApiErrorLogTable.exceptionClassName] = className
                if (fileName != null) it[ApiErrorLogTable.exceptionFileName] = fileName
                if (methodName != null) it[ApiErrorLogTable.exceptionMethodName] = methodName
                if (lineNumber != null) it[ApiErrorLogTable.exceptionLineNumber] = lineNumber
                if (processStatus != null) it[ApiErrorLogTable.processStatus] = processStatus
                if (processTime != null) it[ApiErrorLogTable.processTime] = processTime
                if (processUserId != null) {
                    it[ApiErrorLogTable.processUserId] = Math.toIntExact(processUserId)
                }
                if (updater != null) it[ApiErrorLogTable.updater] = updater
                if (updateTime != null) it[ApiErrorLogTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        ApiErrorLogTable.deleteWhere { ApiErrorLogTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { ApiErrorLogTable.deleteWhere { ApiErrorLogTable.id inList ids } }
    }

    fun deleteByCreateTimeLt(createTime: LocalDateTime, limit: Int): Int {
        if (limit <= 0) return 0
        return transaction {
            val ids = ApiErrorLogTable.selectAll()
                .where { ApiErrorLogTable.createTime less createTime }
                .orderBy(ApiErrorLogTable.id, SortOrder.ASC)
                .limit(limit)
                .map { it[ApiErrorLogTable.id] }
            if (ids.isEmpty()) 0 else ApiErrorLogTable.deleteWhere { ApiErrorLogTable.id inList ids }
        }
    }

    private fun toEntity(row: ResultRow): ApiErrorLogDO = ApiErrorLogDO().apply {
        id = row[ApiErrorLogTable.id]
        traceId = row[ApiErrorLogTable.traceId]
        userId = row[ApiErrorLogTable.userId]
        userType = row[ApiErrorLogTable.userType]
        applicationName = row[ApiErrorLogTable.applicationName]
        requestMethod = row[ApiErrorLogTable.requestMethod]
        requestUrl = row[ApiErrorLogTable.requestUrl]
        requestParams = row[ApiErrorLogTable.requestParams]
        userIp = row[ApiErrorLogTable.userIp]
        userAgent = row[ApiErrorLogTable.userAgent]
        exceptionTime = row[ApiErrorLogTable.exceptionTime]
        exceptionName = row[ApiErrorLogTable.exceptionName]
        exceptionMessage = row[ApiErrorLogTable.exceptionMessage]
        exceptionRootCauseMessage = row[ApiErrorLogTable.exceptionRootCauseMessage]
        exceptionStackTrace = row[ApiErrorLogTable.exceptionStackTrace]
        exceptionClassName = row[ApiErrorLogTable.exceptionClassName]
        exceptionFileName = row[ApiErrorLogTable.exceptionFileName]
        exceptionMethodName = row[ApiErrorLogTable.exceptionMethodName]
        exceptionLineNumber = row[ApiErrorLogTable.exceptionLineNumber]
        processStatus = row[ApiErrorLogTable.processStatus]
        processTime = row[ApiErrorLogTable.processTime]
        processUserId = row[ApiErrorLogTable.processUserId]?.toLong()
        createTime = row[ApiErrorLogTable.createTime]
        updateTime = row[ApiErrorLogTable.updateTime]
        creator = row[ApiErrorLogTable.creator]
        updater = row[ApiErrorLogTable.updater]
        deleted = row[ApiErrorLogTable.deleted]
    }
}
