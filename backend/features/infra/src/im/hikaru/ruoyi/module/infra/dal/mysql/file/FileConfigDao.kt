package im.hikaru.ruoyi.module.infra.dal.mysql.file

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileConfigDO
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

object FileConfigDao {

    fun selectById(id: Long): FileConfigDO? = transaction {
        FileConfigTable.selectAll()
            .where { listOf(FileConfigTable.id eq id, FileConfigTable.deleted eq false).compoundAnd() }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectByIds(ids: Collection<Long>): List<FileConfigDO> {
        if (ids.isEmpty()) return emptyList()
        return transaction {
            FileConfigTable.selectAll()
                .where { listOf(FileConfigTable.id inList ids, FileConfigTable.deleted eq false).compoundAnd() }
                .map(::toEntity)
        }
    }

    fun selectByMaster(): FileConfigDO? = transaction {
        FileConfigTable.selectAll()
            .where { listOf(FileConfigTable.master eq true, FileConfigTable.deleted eq false).compoundAnd() }
            .singleOrNull()
            ?.let(::toEntity)
    }

    fun selectPage(reqVO: FileConfigPageReqVO): PageResult<FileConfigDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(FileConfigTable.deleted eq false)
        reqVO.name?.takeIf(String::isNotEmpty)?.let { conditions += FileConfigTable.name like "%$it%" }
        reqVO.storage?.let { conditions += FileConfigTable.storage eq it }
        reqVO.createTime?.let { (begin, end) ->
            conditions += FileConfigTable.createTime greaterEq begin.toKotlinLocalDateTime()
            conditions += FileConfigTable.createTime lessEq end.toKotlinLocalDateTime()
        }
        FileConfigTable.selectAll()
            .where { conditions.compoundAnd() }
            .orderBy(FileConfigTable.id, SortOrder.DESC)
            .toPageResult(reqVO, ::toEntity)
    }

    fun insert(entity: FileConfigDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val name = requireNotNull(entity.name) { "File config name must not be null" }
        val storage = requireNotNull(entity.storage) { "File config storage must not be null" }
        val config = requireNotNull(entity.config) { "File config data must not be null" }
        val master = requireNotNull(entity.master) { "File config master flag must not be null" }
        val createTime = requireNotNull(entity.createTime) { "File config createTime must not be null" }
        val updateTime = requireNotNull(entity.updateTime) { "File config updateTime must not be null" }
        return transaction {
            FileConfigTable.insert {
                it[FileConfigTable.name] = name
                it[FileConfigTable.storage] = storage
                it[FileConfigTable.config] = JsonUtils.toJsonString(config)
                it[FileConfigTable.master] = master
                it[FileConfigTable.remark] = entity.remark
                it[FileConfigTable.creator] = entity.creator.orEmpty()
                it[FileConfigTable.updater] = entity.updater.orEmpty()
                it[FileConfigTable.createTime] = createTime
                it[FileConfigTable.updateTime] = updateTime
            }.get(FileConfigTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: FileConfigDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id) { "File config id must not be null" }
        val name = entity.name
        val storage = entity.storage
        val config = entity.config
        val master = entity.master
        val remark = entity.remark
        val updater = entity.updater
        val updateTime = entity.updateTime
        transaction {
            FileConfigTable.update(where = { FileConfigTable.id eq id }) {
                if (name != null) it[FileConfigTable.name] = name
                if (storage != null) it[FileConfigTable.storage] = storage
                if (config != null) it[FileConfigTable.config] = JsonUtils.toJsonString(config)
                if (master != null) it[FileConfigTable.master] = master
                if (remark != null) it[FileConfigTable.remark] = remark
                if (updater != null) it[FileConfigTable.updater] = updater
                if (updateTime != null) it[FileConfigTable.updateTime] = updateTime
            }
        }
    }

    fun updateMaster(id: Long) = transaction {
        FileConfigTable.update(where = { FileConfigTable.deleted eq false }) {
            it[master] = false
        }
        FileConfigTable.update(where = { FileConfigTable.id eq id }) {
            it[master] = true
        }
    }

    fun deleteById(id: Long): Int = transaction {
        FileConfigTable.deleteWhere { FileConfigTable.id eq id }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { FileConfigTable.deleteWhere { FileConfigTable.id inList ids } }
    }

    private fun toEntity(row: ResultRow): FileConfigDO = FileConfigDO().apply {
        id = row[FileConfigTable.id]
        name = row[FileConfigTable.name]
        storage = row[FileConfigTable.storage]
        config = requireNotNull(
            JsonUtils.parseObject(
                row[FileConfigTable.config],
                im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig::class.java,
            ),
        )
        master = row[FileConfigTable.master]
        remark = row[FileConfigTable.remark]
        createTime = row[FileConfigTable.createTime]
        updateTime = row[FileConfigTable.updateTime]
        creator = row[FileConfigTable.creator]
        updater = row[FileConfigTable.updater]
        deleted = row[FileConfigTable.deleted]
    }
}
