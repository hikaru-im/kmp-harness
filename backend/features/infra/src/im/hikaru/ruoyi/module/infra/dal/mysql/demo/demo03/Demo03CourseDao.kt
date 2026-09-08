package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03CourseDO
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import im.hikaru.ruoyi.framework.mybatis.core.mapper.update

object Demo03CourseDao {

    fun selectById(id: Long): Demo03CourseDO? = transaction {
        Demo03CourseTable.selectAll()
            .where {
                listOf(
                    Demo03CourseTable.id eq id,
                    Demo03CourseTable.deleted eq false,
                ).compoundAnd()
            }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<Demo03CourseDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            Demo03CourseTable.selectAll()
                .where {
                    listOf(
                        Demo03CourseTable.id inList ids,
                        Demo03CourseTable.deleted eq false,
                    ).compoundAnd()
                }
                .map(::toEntity)
        }
    }

    fun selectListByStudentId(studentId: Long): List<Demo03CourseDO> = transaction {
        Demo03CourseTable.selectAll()
            .where {
                listOf(
                    Demo03CourseTable.studentId eq studentId,
                    Demo03CourseTable.deleted eq false,
                ).compoundAnd()
            }
            .orderBy(Demo03CourseTable.id, SortOrder.ASC)
            .map(::toEntity)
    }

    fun selectPage(pageParam: PageParam, studentId: Long): PageResult<Demo03CourseDO> = transaction {
        Demo03CourseTable.selectAll()
            .where {
                listOf(
                    Demo03CourseTable.studentId eq studentId,
                    Demo03CourseTable.deleted eq false,
                ).compoundAnd()
            }
            .orderBy(Demo03CourseTable.id, SortOrder.DESC)
            .toPageResult(pageParam, ::toEntity)
    }

    fun insert(entity: Demo03CourseDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val studentId = requireNotNull(entity.studentId)
        val name = requireNotNull(entity.name)
        val score = requireNotNull(entity.score)
        val createTime = requireNotNull(entity.createTime)
        val updateTime = requireNotNull(entity.updateTime)
        return transaction {
            Demo03CourseTable.insert {
                it[Demo03CourseTable.studentId] = studentId
                it[Demo03CourseTable.name] = name
                it[Demo03CourseTable.score] = score
                it[Demo03CourseTable.tenantId] = TenantContextHolder.getTenantId() ?: 0L
                it[Demo03CourseTable.creator] = entity.creator.orEmpty()
                it[Demo03CourseTable.updater] = entity.updater.orEmpty()
                it[Demo03CourseTable.createTime] = createTime
                it[Demo03CourseTable.updateTime] = updateTime
            }.get(Demo03CourseTable.id)
        }.also { entity.id = it }
    }

    fun insertBatch(entities: Collection<Demo03CourseDO>) {
        entities.forEach(::insert)
    }

    fun updateById(entity: Demo03CourseDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        val studentId = entity.studentId
        val name = entity.name
        val score = entity.score
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            Demo03CourseTable.update(where = { Demo03CourseTable.id eq id }) {
                if (studentId != null) it[Demo03CourseTable.studentId] = studentId
                if (name != null) it[Demo03CourseTable.name] = name
                if (score != null) it[Demo03CourseTable.score] = score
                if (updater != null) it[Demo03CourseTable.updater] = updater
                if (updateTime != null) it[Demo03CourseTable.updateTime] = updateTime
            }
        }
    }

    fun updateBatch(entities: Collection<Demo03CourseDO>) {
        entities.forEach(::updateById)
    }

    fun deleteById(id: Long): Int = transaction {
        Demo03CourseTable.deleteWhere { Demo03CourseTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { Demo03CourseTable.deleteWhere { Demo03CourseTable.id inList ids } }
    }

    fun deleteByStudentId(studentId: Long): Int = transaction {
        Demo03CourseTable.deleteWhere { Demo03CourseTable.studentId eq studentId }
    }

    fun deleteByStudentIds(studentIds: Collection<Long>): Int {
        if (studentIds.isEmpty()) return 0
        return transaction { Demo03CourseTable.deleteWhere { Demo03CourseTable.studentId inList studentIds } }
    }

    private fun toEntity(row: ResultRow): Demo03CourseDO = Demo03CourseDO().apply {
        id = row[Demo03CourseTable.id]
        studentId = row[Demo03CourseTable.studentId]
        name = row[Demo03CourseTable.name]
        score = row[Demo03CourseTable.score]
        creator = row[Demo03CourseTable.creator]
        createTime = row[Demo03CourseTable.createTime]
        updater = row[Demo03CourseTable.updater]
        updateTime = row[Demo03CourseTable.updateTime]
        deleted = row[Demo03CourseTable.deleted]
    }
}
