package im.hikaru.ruoyi.module.infra.dal.mysql.codegen

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.framework.mybatis.core.mapper.toPageResult
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.CodegenTablePageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenTableDO
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

object CodegenTableDao {
    fun selectById(id: Long): CodegenTableDO? = transaction {
        CodegenTable.selectAll()
            .where { listOf(CodegenTable.id eq id, CodegenTable.deleted eq false).compoundAnd() }
            .singleOrNull()?.let(::toEntity)
    }

    fun selectByTableNameAndDataSourceConfigId(tableName: String, dataSourceConfigId: Long): CodegenTableDO? =
        transaction {
            CodegenTable.selectAll().where {
                listOf(
                    CodegenTable.tableNameColumn eq tableName,
                    CodegenTable.dataSourceConfigId eq dataSourceConfigId,
                    CodegenTable.deleted eq false,
                ).compoundAnd()
            }.singleOrNull()?.let(::toEntity)
        }

    fun selectPage(req: CodegenTablePageReqVO): PageResult<CodegenTableDO> = transaction {
        val conditions = mutableListOf<Op<Boolean>>(CodegenTable.deleted eq false)
        req.tableName?.takeIf(String::isNotEmpty)?.let { conditions += CodegenTable.tableNameColumn like "%$it%" }
        req.tableComment?.takeIf(String::isNotEmpty)?.let { conditions += CodegenTable.tableComment like "%$it%" }
        req.className?.takeIf(String::isNotEmpty)?.let { conditions += CodegenTable.className like "%$it%" }
        req.createTime?.let { (begin, end) ->
            conditions += CodegenTable.createTime greaterEq begin.toKotlinLocalDateTime()
            conditions += CodegenTable.createTime lessEq end.toKotlinLocalDateTime()
        }
        CodegenTable.selectAll().where { conditions.compoundAnd() }
            .orderBy(CodegenTable.updateTime, SortOrder.DESC)
            .toPageResult(req, ::toEntity)
    }

    fun selectListByDataSourceConfigId(dataSourceConfigId: Long): List<CodegenTableDO> = transaction {
        CodegenTable.selectAll().where {
            listOf(CodegenTable.dataSourceConfigId eq dataSourceConfigId, CodegenTable.deleted eq false).compoundAnd()
        }.map(::toEntity)
    }

    fun selectListByTemplateTypeAndMasterTableId(templateType: Int, masterTableId: Long): List<CodegenTableDO> =
        transaction {
            CodegenTable.selectAll().where {
                listOf(
                    CodegenTable.templateType eq templateType,
                    CodegenTable.masterTableId eq masterTableId,
                    CodegenTable.deleted eq false,
                ).compoundAnd()
            }.map(::toEntity)
        }

    fun insert(entity: CodegenTableDO): Long {
        DefaultDBFieldHandler.fillOnInsert(entity)
        val values = requiredValues(entity)
        return transaction {
            CodegenTable.insert { statement ->
                statement[CodegenTable.dataSourceConfigId] = values.dataSourceConfigId
                statement[CodegenTable.scene] = values.scene
                statement[CodegenTable.tableNameColumn] = values.tableName
                statement[CodegenTable.tableComment] = values.tableComment
                statement[CodegenTable.remark] = entity.remark
                statement[CodegenTable.moduleName] = values.moduleName
                statement[CodegenTable.businessName] = values.businessName
                statement[CodegenTable.className] = values.className
                statement[CodegenTable.classComment] = values.classComment
                statement[CodegenTable.author] = values.author
                statement[CodegenTable.templateType] = values.templateType
                statement[CodegenTable.frontType] = values.frontType
                statement[CodegenTable.parentMenuId] = entity.parentMenuId
                statement[CodegenTable.masterTableId] = entity.masterTableId
                statement[CodegenTable.subJoinColumnId] = entity.subJoinColumnId
                statement[CodegenTable.subJoinMany] = entity.subJoinMany
                statement[CodegenTable.treeParentColumnId] = entity.treeParentColumnId
                statement[CodegenTable.treeNameColumnId] = entity.treeNameColumnId
                statement[CodegenTable.creator] = entity.creator.orEmpty()
                statement[CodegenTable.updater] = entity.updater.orEmpty()
                statement[CodegenTable.createTime] = values.createTime
                statement[CodegenTable.updateTime] = values.updateTime
            }.get(CodegenTable.id)
        }.also { entity.id = it }
    }

    fun updateById(entity: CodegenTableDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        transaction {
            CodegenTable.update(where = { CodegenTable.id eq id }) { statement ->
                entity.dataSourceConfigId?.let { statement[CodegenTable.dataSourceConfigId] = it }
                entity.scene?.let { statement[CodegenTable.scene] = it }
                entity.tableName?.let { statement[CodegenTable.tableNameColumn] = it }
                entity.tableComment?.let { statement[CodegenTable.tableComment] = it }
                entity.remark?.let { statement[CodegenTable.remark] = it }
                entity.moduleName?.let { statement[CodegenTable.moduleName] = it }
                entity.businessName?.let { statement[CodegenTable.businessName] = it }
                entity.className?.let { statement[CodegenTable.className] = it }
                entity.classComment?.let { statement[CodegenTable.classComment] = it }
                entity.author?.let { statement[CodegenTable.author] = it }
                entity.templateType?.let { statement[CodegenTable.templateType] = it }
                entity.frontType?.let { statement[CodegenTable.frontType] = it }
                entity.parentMenuId?.let { statement[CodegenTable.parentMenuId] = it }
                entity.masterTableId?.let { statement[CodegenTable.masterTableId] = it }
                entity.subJoinColumnId?.let { statement[CodegenTable.subJoinColumnId] = it }
                entity.subJoinMany?.let { statement[CodegenTable.subJoinMany] = it }
                entity.treeParentColumnId?.let { statement[CodegenTable.treeParentColumnId] = it }
                entity.treeNameColumnId?.let { statement[CodegenTable.treeNameColumnId] = it }
                entity.updater?.let { statement[CodegenTable.updater] = it }
                entity.updateTime?.let { statement[CodegenTable.updateTime] = it }
            }
        }
    }

    fun deleteById(id: Long): Int = transaction { CodegenTable.deleteWhere { CodegenTable.id eq id } }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { CodegenTable.deleteWhere { CodegenTable.id inList ids } }
    }

    private data class RequiredValues(
        val dataSourceConfigId: Long, val scene: Int, val tableName: String, val tableComment: String,
        val moduleName: String, val businessName: String, val className: String, val classComment: String,
        val author: String, val templateType: Int, val frontType: Int,
        val createTime: kotlinx.datetime.LocalDateTime, val updateTime: kotlinx.datetime.LocalDateTime,
    )

    private fun requiredValues(entity: CodegenTableDO) = RequiredValues(
        requireNotNull(entity.dataSourceConfigId), requireNotNull(entity.scene), requireNotNull(entity.tableName),
        requireNotNull(entity.tableComment), requireNotNull(entity.moduleName), requireNotNull(entity.businessName),
        requireNotNull(entity.className), requireNotNull(entity.classComment), requireNotNull(entity.author),
        requireNotNull(entity.templateType), requireNotNull(entity.frontType), requireNotNull(entity.createTime),
        requireNotNull(entity.updateTime),
    )

    private fun toEntity(row: ResultRow): CodegenTableDO = CodegenTableDO().apply {
        id = row[CodegenTable.id]
        dataSourceConfigId = row[CodegenTable.dataSourceConfigId]
        scene = row[CodegenTable.scene]
        tableName = row[CodegenTable.tableNameColumn]
        tableComment = row[CodegenTable.tableComment]
        remark = row[CodegenTable.remark]
        moduleName = row[CodegenTable.moduleName]
        businessName = row[CodegenTable.businessName]
        className = row[CodegenTable.className]
        classComment = row[CodegenTable.classComment]
        author = row[CodegenTable.author]
        templateType = row[CodegenTable.templateType]
        frontType = row[CodegenTable.frontType]
        parentMenuId = row[CodegenTable.parentMenuId]
        masterTableId = row[CodegenTable.masterTableId]
        subJoinColumnId = row[CodegenTable.subJoinColumnId]
        subJoinMany = row[CodegenTable.subJoinMany]
        treeParentColumnId = row[CodegenTable.treeParentColumnId]
        treeNameColumnId = row[CodegenTable.treeNameColumnId]
        creator = row[CodegenTable.creator]
        createTime = row[CodegenTable.createTime]
        updater = row[CodegenTable.updater]
        updateTime = row[CodegenTable.updateTime]
        deleted = row[CodegenTable.deleted]
    }
}
