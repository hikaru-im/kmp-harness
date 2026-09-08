package im.hikaru.ruoyi.module.infra.dal.mysql.job

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.log.JobLogPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.job.JobLogDO
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

object JobLogDao {

    fun selectById(id: Long): JobLogDO? = transaction {
        JobLogTable.selectAll()
            .where { listOf(JobLogTable.id eq id, JobLogTable.deleted eq false).compoundAnd() }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<JobLogDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            JobLogTable.selectAll()
                .where { listOf(JobLogTable.id inList ids, JobLogTable.deleted eq false).compoundAnd() }
                .map(::toEntity)
        }
    }

    fun selectPage(reqVO: JobLogPageReqVO): PageResult<JobLogDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(JobLogTable.deleted eq false)
        reqVO.jobId?.let { conditions += JobLogTable.jobId eq it }
        reqVO.handlerName?.takeIf(String::isNotEmpty)?.let {
            conditions += JobLogTable.handlerName like "%$it%"
        }
        reqVO.beginTime?.let { conditions += JobLogTable.beginTime greaterEq it.toKotlinLocalDateTime() }
        reqVO.endTime?.let { conditions += JobLogTable.endTime lessEq it.toKotlinLocalDateTime() }
        reqVO.status?.let { conditions += JobLogTable.status eq it }
        JobLogTable.selectAll()
            .where { conditions.compoundAnd() }
            .orderBy(JobLogTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }

    fun insert(entity: JobLogDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val jobId = requireNotNull(entity.jobId) { "Job log jobId must not be null" }
        val handlerName = requireNotNull(entity.handlerName) { "Job log handlerName must not be null" }
        val executeIndex = requireNotNull(entity.executeIndex) { "Job log executeIndex must not be null" }
        val beginTime = requireNotNull(entity.beginTime) { "Job log beginTime must not be null" }
        val status = requireNotNull(entity.status) { "Job log status must not be null" }
        val createTime = requireNotNull(entity.createTime) { "Job log createTime must not be null" }
        val updateTime = requireNotNull(entity.updateTime) { "Job log updateTime must not be null" }
        return transaction {
            JobLogTable.insert {
                it[JobLogTable.jobId] = jobId
                it[JobLogTable.handlerName] = handlerName
                it[JobLogTable.handlerParam] = entity.handlerParam
                it[JobLogTable.executeIndex] = executeIndex
                it[JobLogTable.beginTime] = beginTime
                it[JobLogTable.endTime] = entity.endTime
                it[JobLogTable.duration] = entity.duration
                it[JobLogTable.status] = status
                it[JobLogTable.result] = entity.result
                it[JobLogTable.creator] = entity.creator.orEmpty()
                it[JobLogTable.updater] = entity.updater.orEmpty()
                it[JobLogTable.createTime] = createTime
                it[JobLogTable.updateTime] = updateTime
            }.get(JobLogTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: JobLogDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id) { "Job log id must not be null" }
        val jobId = entity.jobId
        val handlerName = entity.handlerName
        val handlerParam = entity.handlerParam
        val executeIndex = entity.executeIndex
        val beginTime = entity.beginTime
        val endTime = entity.endTime
        val duration = entity.duration
        val status = entity.status
        val result = entity.result
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            JobLogTable.update(where = { JobLogTable.id eq id }) {
                if (jobId != null) it[JobLogTable.jobId] = jobId
                if (handlerName != null) it[JobLogTable.handlerName] = handlerName
                if (handlerParam != null) it[JobLogTable.handlerParam] = handlerParam
                if (executeIndex != null) it[JobLogTable.executeIndex] = executeIndex
                if (beginTime != null) it[JobLogTable.beginTime] = beginTime
                if (endTime != null) it[JobLogTable.endTime] = endTime
                if (duration != null) it[JobLogTable.duration] = duration
                if (status != null) it[JobLogTable.status] = status
                if (result != null) it[JobLogTable.result] = result
                if (updater != null) it[JobLogTable.updater] = updater
                if (updateTime != null) it[JobLogTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        JobLogTable.deleteWhere { JobLogTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { JobLogTable.deleteWhere { JobLogTable.id inList ids } }
    }

    fun deleteByCreateTimeLt(createTime: LocalDateTime, limit: Int): Int {
        if (limit <= 0) return 0
        return transaction {
            val ids = JobLogTable.selectAll()
                .where { JobLogTable.createTime less createTime }
                .orderBy(JobLogTable.id, SortOrder.ASC)
                .limit(limit)
                .map { it[JobLogTable.id] }
            if (ids.isEmpty()) 0 else JobLogTable.deleteWhere { JobLogTable.id inList ids }
        }
    }

    private fun toEntity(row: ResultRow): JobLogDO = JobLogDO().apply {
        id = row[JobLogTable.id]
        jobId = row[JobLogTable.jobId]
        handlerName = row[JobLogTable.handlerName]
        handlerParam = row[JobLogTable.handlerParam]
        executeIndex = row[JobLogTable.executeIndex]
        beginTime = row[JobLogTable.beginTime]
        endTime = row[JobLogTable.endTime]
        duration = row[JobLogTable.duration]
        status = row[JobLogTable.status]
        result = row[JobLogTable.result]
        createTime = row[JobLogTable.createTime]
        updateTime = row[JobLogTable.updateTime]
        creator = row[JobLogTable.creator]
        updater = row[JobLogTable.updater]
        deleted = row[JobLogTable.deleted]
    }
}
