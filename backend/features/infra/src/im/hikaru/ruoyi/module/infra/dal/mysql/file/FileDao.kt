package im.hikaru.ruoyi.module.infra.dal.mysql.file

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FilePageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileDO
import kotlinx.datetime.toKotlinLocalDateTime
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

object FileDao {

    fun selectById(id: Long): FileDO? = transaction {
        FileTable.selectAll()
            .where { listOf(FileTable.id eq id, FileTable.deleted eq false).compoundAnd() }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<FileDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            FileTable.selectAll()
                .where { listOf(FileTable.id inList ids, FileTable.deleted eq false).compoundAnd() }
                .map(::toEntity)
        }
    }

    fun selectPage(reqVO: FilePageReqVO): PageResult<FileDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(FileTable.deleted eq false)
        reqVO.path?.takeIf(String::isNotEmpty)?.let { conditions += FileTable.path like "%$it%" }
        reqVO.type?.takeIf(String::isNotEmpty)?.let { conditions += FileTable.type like "%$it%" }
        reqVO.createTime?.let { (begin, end) ->
            conditions += FileTable.createTime greaterEq begin.toKotlinLocalDateTime()
            conditions += FileTable.createTime lessEq end.toKotlinLocalDateTime()
        }
        FileTable.selectAll()
            .where { conditions.compoundAnd() }
            .orderBy(FileTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }

    fun selectLatestByConfigIdAndPath(configId: Long, path: String): FileDO? = transaction {
        FileTable.selectAll()
            .where {
                listOf(
                    FileTable.configId eq configId,
                    FileTable.path eq path,
                    FileTable.deleted eq false,
                ).compoundAnd()
            }
            .orderBy(FileTable.id, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun insert(entity: FileDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val path = requireNotNull(entity.path) { "File path must not be null" }
        val url = requireNotNull(entity.url) { "File URL must not be null" }
        val size = requireNotNull(entity.size) { "File size must not be null" }
        val createTime = requireNotNull(entity.createTime) { "File createTime must not be null" }
        val updateTime = requireNotNull(entity.updateTime) { "File updateTime must not be null" }
        return transaction {
            FileTable.insert {
                it[FileTable.configId] = entity.configId
                it[FileTable.name] = entity.name
                it[FileTable.path] = path
                it[FileTable.url] = url
                it[FileTable.type] = entity.type
                it[FileTable.size] = Math.toIntExact(size)
                it[FileTable.creator] = entity.creator.orEmpty()
                it[FileTable.updater] = entity.updater.orEmpty()
                it[FileTable.createTime] = createTime
                it[FileTable.updateTime] = updateTime
            }.get(FileTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: FileDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id) { "File id must not be null" }
        val configId = entity.configId
        val name = entity.name
        val path = entity.path
        val url = entity.url
        val type = entity.type
        val size = entity.size
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            FileTable.update(where = { FileTable.id eq id }) {
                if (configId != null) it[FileTable.configId] = configId
                if (name != null) it[FileTable.name] = name
                if (path != null) it[FileTable.path] = path
                if (url != null) it[FileTable.url] = url
                if (type != null) it[FileTable.type] = type
                if (size != null) it[FileTable.size] = Math.toIntExact(size)
                if (updater != null) it[FileTable.updater] = updater
                if (updateTime != null) it[FileTable.updateTime] = updateTime
            }
        }
    }

    fun deleteById(id: Long): Int = transaction {
        FileTable.deleteWhere { FileTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { FileTable.deleteWhere { FileTable.id inList ids } }
    }

    private fun toEntity(row: ResultRow): FileDO = FileDO().apply {
        id = row[FileTable.id]
        configId = row[FileTable.configId]
        name = row[FileTable.name]
        path = row[FileTable.path]
        url = row[FileTable.url]
        type = row[FileTable.type]
        size = row[FileTable.size].toLong()
        createTime = row[FileTable.createTime]
        updateTime = row[FileTable.updateTime]
        creator = row[FileTable.creator]
        updater = row[FileTable.updater]
        deleted = row[FileTable.deleted]
    }
}
