package im.hikaru.ruoyi.module.infra.dal.mysql.job

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.infra.controller.admin.job.vo.job.JobPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.job.JobDO
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object JobDao {

    fun selectById(id: Long): JobDO? = transaction {
        JobTable.selectAll()
            .where { listOf(JobTable.id eq id, JobTable.deleted eq false).compoundAnd() }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<JobDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            JobTable.selectAll()
                .where { listOf(JobTable.id inList ids, JobTable.deleted eq false).compoundAnd() }
                .map(::toEntity)
        }
    }

    fun selectByHandlerName(handlerName: String): JobDO? = transaction {
        JobTable.selectAll()
            .where {
                listOf(
                    JobTable.handlerName eq handlerName,
                    JobTable.deleted eq false,
                ).compoundAnd()
            }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectList(): List<JobDO> = transaction {
        JobTable.selectAll()
            .where { JobTable.deleted eq false }
            .orderBy(JobTable.id, SortOrder.ASC)
            .map(::toEntity)
    }

    fun selectPage(reqVO: JobPageReqVO): PageResult<JobDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(JobTable.deleted eq false)
        reqVO.name?.takeIf(String::isNotEmpty)?.let { conditions += JobTable.name like "%$it%" }
        reqVO.status?.let { conditions += JobTable.status eq it }
        reqVO.handlerName?.takeIf(String::isNotEmpty)?.let {
            conditions += JobTable.handlerName like "%$it%"
        }
        JobTable.selectAll()
            .where { conditions.compoundAnd() }
            .orderBy(JobTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }

    fun insert(entity: JobDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val name = requireNotNull(entity.name) { "Job name must not be null" }
        val status = requireNotNull(entity.status) { "Job status must not be null" }
        val handlerName = requireNotNull(entity.handlerName) { "Job handlerName must not be null" }
        val cronExpression = requireNotNull(entity.cronExpression) { "Job cronExpression must not be null" }
        val retryCount = requireNotNull(entity.retryCount) { "Job retryCount must not be null" }
        val retryInterval = requireNotNull(entity.retryInterval) { "Job retryInterval must not be null" }
        val monitorTimeout = requireNotNull(entity.monitorTimeout) { "Job monitorTimeout must not be null" }
        val createTime = requireNotNull(entity.createTime) { "Job createTime must not be null" }
        val updateTime = requireNotNull(entity.updateTime) { "Job updateTime must not be null" }
        return transaction {
            JobTable.insert {
                it[JobTable.name] = name
                it[JobTable.status] = status
                it[JobTable.handlerName] = handlerName
                it[JobTable.handlerParam] = entity.handlerParam
                it[JobTable.cronExpression] = cronExpression
                it[JobTable.retryCount] = retryCount
                it[JobTable.retryInterval] = retryInterval
                it[JobTable.monitorTimeout] = monitorTimeout
                it[JobTable.creator] = entity.creator.orEmpty()
                it[JobTable.updater] = entity.updater.orEmpty()
                it[JobTable.createTime] = createTime
                it[JobTable.updateTime] = updateTime
            }.get(JobTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: JobDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id) { "Job id must not be null" }
        val name = entity.name
        val status = entity.status
        val handlerName = entity.handlerName
        val handlerParam = entity.handlerParam
        val cronExpression = entity.cronExpression
        val retryCount = entity.retryCount
        val retryInterval = entity.retryInterval
        val monitorTimeout = entity.monitorTimeout
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            JobTable.update(where = { JobTable.id eq id }) {
                if (name != null) it[JobTable.name] = name
                if (status != null) it[JobTable.status] = status
                if (handlerName != null) it[JobTable.handlerName] = handlerName
                if (handlerParam != null) it[JobTable.handlerParam] = handlerParam
                if (cronExpression != null) it[JobTable.cronExpression] = cronExpression
                if (retryCount != null) it[JobTable.retryCount] = retryCount
                if (retryInterval != null) it[JobTable.retryInterval] = retryInterval
                if (monitorTimeout != null) it[JobTable.monitorTimeout] = monitorTimeout
                if (updater != null) it[JobTable.updater] = updater
                if (updateTime != null) it[JobTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        JobTable.deleteWhere { JobTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { JobTable.deleteWhere { JobTable.id inList ids } }
    }

    private fun toEntity(row: ResultRow): JobDO = JobDO().apply {
        id = row[JobTable.id]
        name = row[JobTable.name]
        status = row[JobTable.status]
        handlerName = row[JobTable.handlerName]
        handlerParam = row[JobTable.handlerParam]
        cronExpression = row[JobTable.cronExpression]
        retryCount = row[JobTable.retryCount]
        retryInterval = row[JobTable.retryInterval]
        monitorTimeout = row[JobTable.monitorTimeout]
        createTime = row[JobTable.createTime]
        updateTime = row[JobTable.updateTime]
        creator = row[JobTable.creator]
        updater = row[JobTable.updater]
        deleted = row[JobTable.deleted]
    }
}
