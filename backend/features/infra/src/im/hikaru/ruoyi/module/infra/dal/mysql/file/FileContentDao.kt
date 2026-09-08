package im.hikaru.ruoyi.module.infra.dal.mysql.file

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileContentDO
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object FileContentDao {
    fun selectLatestByConfigIdAndPath(configId: Long, path: String): FileContentDO? = transaction {
        FileContentTable.selectAll()
            .where {
                listOf(
                    FileContentTable.configId eq configId,
                    FileContentTable.path eq path,
                    FileContentTable.deleted eq false,
                ).compoundAnd()
            }
            .orderBy(FileContentTable.id, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun insert(entity: FileContentDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val configId = requireNotNull(entity.configId)
        val path = requireNotNull(entity.path)
        val content = requireNotNull(entity.content)
        val createTime = requireNotNull(entity.createTime)
        val updateTime = requireNotNull(entity.updateTime)
        return transaction {
            FileContentTable.insert {
                it[FileContentTable.configId] = configId
                it[FileContentTable.path] = path
                it[FileContentTable.content] = content
                it[FileContentTable.creator] = entity.creator.orEmpty()
                it[FileContentTable.updater] = entity.updater.orEmpty()
                it[FileContentTable.createTime] = createTime
                it[FileContentTable.updateTime] = updateTime
            }.get(FileContentTable.id)
        }.also { entity.id = it }
    }

    fun deleteByConfigIdAndPath(configId: Long, path: String): Int = transaction {
        FileContentTable.deleteWhere {
            listOf(FileContentTable.configId eq configId, FileContentTable.path eq path).compoundAnd()
        }
    }

    private fun toEntity(row: ResultRow): FileContentDO = FileContentDO().apply {
        id = row[FileContentTable.id]
        configId = row[FileContentTable.configId]
        path = row[FileContentTable.path]
        content = row[FileContentTable.content]
        creator = row[FileContentTable.creator]
        createTime = row[FileContentTable.createTime]
        updater = row[FileContentTable.updater]
        updateTime = row[FileContentTable.updateTime]
        deleted = row[FileContentTable.deleted]
    }
}
