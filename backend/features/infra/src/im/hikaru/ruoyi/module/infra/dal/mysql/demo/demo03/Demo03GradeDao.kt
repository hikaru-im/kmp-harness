package im.hikaru.ruoyi.module.infra.dal.mysql.demo.demo03

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo03.Demo03GradeDO
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

object Demo03GradeDao {

    fun selectById(id: Long): Demo03GradeDO? = transaction {
        Demo03GradeTable.selectAll()
            .where {
                listOf(
                    Demo03GradeTable.id eq id,
                    Demo03GradeTable.deleted eq false,
                ).compoundAnd()
            }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByStudentId(studentId: Long): Demo03GradeDO? = transaction {
        Demo03GradeTable.selectAll()
            .where {
                listOf(
                    Demo03GradeTable.studentId eq studentId,
                    Demo03GradeTable.deleted eq false,
                ).compoundAnd()
            }
            .orderBy(Demo03GradeTable.id, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectPage(pageParam: PageParam, studentId: Long): PageResult<Demo03GradeDO> = transaction {
        Demo03GradeTable.selectAll()
            .where {
                listOf(
                    Demo03GradeTable.studentId eq studentId,
                    Demo03GradeTable.deleted eq false,
                ).compoundAnd()
            }
            .orderBy(Demo03GradeTable.id, SortOrder.DESC)
            .toPageResult(pageParam, ::toEntity)
    }

    fun insert(entity: Demo03GradeDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val studentId = requireNotNull(entity.studentId)
        val name = requireNotNull(entity.name)
        val teacher = requireNotNull(entity.teacher)
        val createTime = requireNotNull(entity.createTime)
        val updateTime = requireNotNull(entity.updateTime)
        return transaction {
            Demo03GradeTable.insert {
                it[Demo03GradeTable.studentId] = studentId
                it[Demo03GradeTable.name] = name
                it[Demo03GradeTable.teacher] = teacher
                it[Demo03GradeTable.tenantId] = TenantContextHolder.getTenantId() ?: 0L
                it[Demo03GradeTable.creator] = entity.creator.orEmpty()
                it[Demo03GradeTable.updater] = entity.updater.orEmpty()
                it[Demo03GradeTable.createTime] = createTime
                it[Demo03GradeTable.updateTime] = updateTime
            }.get(Demo03GradeTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: Demo03GradeDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        val studentId = entity.studentId
        val name = entity.name
        val teacher = entity.teacher
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            Demo03GradeTable.update(where = { Demo03GradeTable.id eq id }) {
                if (studentId != null) it[Demo03GradeTable.studentId] = studentId
                if (name != null) it[Demo03GradeTable.name] = name
                if (teacher != null) it[Demo03GradeTable.teacher] = teacher
                if (updater != null) it[Demo03GradeTable.updater] = updater
                if (updateTime != null) it[Demo03GradeTable.updateTime] = updateTime
            }
        }
    }

    fun insertOrUpdate(entity: Demo03GradeDO) {
        val existing = entity.id?.let(::selectById)
            ?: selectByStudentId(requireNotNull(entity.studentId))
        if (existing == null) {
            insert(entity)
        } else {
            entity.id = existing.id
            updateById(entity)
        }
    }

    fun deleteById(id: Long): Int = transaction {
        Demo03GradeTable.deleteWhere { Demo03GradeTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { Demo03GradeTable.deleteWhere { Demo03GradeTable.id inList ids } }
    }

    fun deleteByStudentId(studentId: Long): Int = transaction {
        Demo03GradeTable.deleteWhere { Demo03GradeTable.studentId eq studentId }
    }

    fun deleteByStudentIds(studentIds: Collection<Long>): Int {
        if (studentIds.isEmpty()) return 0
        return transaction { Demo03GradeTable.deleteWhere { Demo03GradeTable.studentId inList studentIds } }
    }

    private fun toEntity(row: ResultRow): Demo03GradeDO = Demo03GradeDO().apply {
        id = row[Demo03GradeTable.id]
        studentId = row[Demo03GradeTable.studentId]
        name = row[Demo03GradeTable.name]
        teacher = row[Demo03GradeTable.teacher]
        creator = row[Demo03GradeTable.creator]
        createTime = row[Demo03GradeTable.createTime]
        updater = row[Demo03GradeTable.updater]
        updateTime = row[Demo03GradeTable.updateTime]
        deleted = row[Demo03GradeTable.deleted]
    }
}
