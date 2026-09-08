package im.hikaru.ruoyi.module.infra.dal.mysql.codegen

import im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler
import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenColumnDO
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

object CodegenColumnDao {
    fun selectListByTableId(tableId: Long): List<CodegenColumnDO> = transaction {
        CodegenColumnTable.selectAll().where {
            listOf(CodegenColumnTable.tableId eq tableId, CodegenColumnTable.deleted eq false).compoundAnd()
        }.orderBy(CodegenColumnTable.ordinalPosition, SortOrder.ASC).map(::toEntity)
    }

    fun insertBatch(entities: Collection<CodegenColumnDO>) {
        entities.forEach(DefaultDBFieldHandler::fillOnInsert)
        transaction {
            entities.forEach { entity ->
                val values = requiredValues(entity)
                entity.id = CodegenColumnTable.insert { statement ->
                    statement[tableId] = values.tableId
                    statement[columnName] = values.columnName
                    statement[dataType] = values.dataType
                    statement[columnComment] = values.columnComment
                    statement[nullable] = values.nullable
                    statement[primaryKeyFlag] = values.primaryKey
                    statement[ordinalPosition] = values.ordinalPosition
                    statement[javaType] = values.javaType
                    statement[javaField] = values.javaField
                    statement[dictType] = entity.dictType
                    statement[example] = entity.example
                    statement[createOperation] = values.createOperation
                    statement[updateOperation] = values.updateOperation
                    statement[listOperation] = values.listOperation
                    statement[listOperationCondition] = values.listOperationCondition
                    statement[listOperationResult] = values.listOperationResult
                    statement[htmlType] = values.htmlType
                    statement[creator] = entity.creator.orEmpty()
                    statement[updater] = entity.updater.orEmpty()
                    statement[createTime] = values.createTime
                    statement[updateTime] = values.updateTime
                }.get(CodegenColumnTable.id)
            }
        }
    }

    fun updateById(entity: CodegenColumnDO) {
        DefaultDBFieldHandler.fillOnUpdate(entity)
        val id = requireNotNull(entity.id)
        transaction {
            CodegenColumnTable.update(where = { CodegenColumnTable.id eq id }) { statement ->
                entity.tableId?.let { statement[tableId] = it }
                entity.columnName?.let { statement[columnName] = it }
                entity.dataType?.let { statement[dataType] = it }
                entity.columnComment?.let { statement[columnComment] = it }
                entity.nullable?.let { statement[nullable] = it }
                entity.primaryKey?.let { statement[primaryKeyFlag] = it }
                entity.ordinalPosition?.let { statement[ordinalPosition] = it }
                entity.javaType?.let { statement[javaType] = it }
                entity.javaField?.let { statement[javaField] = it }
                entity.dictType?.let { statement[dictType] = it }
                entity.example?.let { statement[example] = it }
                entity.createOperation?.let { statement[createOperation] = it }
                entity.updateOperation?.let { statement[updateOperation] = it }
                entity.listOperation?.let { statement[listOperation] = it }
                entity.listOperationCondition?.let { statement[listOperationCondition] = it }
                entity.listOperationResult?.let { statement[listOperationResult] = it }
                entity.htmlType?.let { statement[htmlType] = it }
                entity.updater?.let { statement[updater] = it }
                entity.updateTime?.let { statement[updateTime] = it }
            }
        }
    }

    fun deleteByIds(ids: Collection<Long>): Int {
        if (ids.isEmpty()) return 0
        return transaction { CodegenColumnTable.deleteWhere { CodegenColumnTable.id inList ids } }
    }

    fun deleteListByTableId(tableId: Long): Int = transaction {
        CodegenColumnTable.deleteWhere { CodegenColumnTable.tableId eq tableId }
    }

    fun deleteListByTableIds(tableIds: Collection<Long>): Int {
        if (tableIds.isEmpty()) return 0
        return transaction { CodegenColumnTable.deleteWhere { CodegenColumnTable.tableId inList tableIds } }
    }

    private data class RequiredValues(
        val tableId: Long, val columnName: String, val dataType: String, val columnComment: String,
        val nullable: Boolean, val primaryKey: Boolean, val ordinalPosition: Int, val javaType: String,
        val javaField: String, val createOperation: Boolean, val updateOperation: Boolean,
        val listOperation: Boolean, val listOperationCondition: String, val listOperationResult: Boolean,
        val htmlType: String, val createTime: kotlinx.datetime.LocalDateTime,
        val updateTime: kotlinx.datetime.LocalDateTime,
    )

    private fun requiredValues(entity: CodegenColumnDO) = RequiredValues(
        requireNotNull(entity.tableId), requireNotNull(entity.columnName), requireNotNull(entity.dataType),
        requireNotNull(entity.columnComment), requireNotNull(entity.nullable), requireNotNull(entity.primaryKey),
        requireNotNull(entity.ordinalPosition), requireNotNull(entity.javaType), requireNotNull(entity.javaField),
        requireNotNull(entity.createOperation), requireNotNull(entity.updateOperation), requireNotNull(entity.listOperation),
        requireNotNull(entity.listOperationCondition), requireNotNull(entity.listOperationResult),
        requireNotNull(entity.htmlType), requireNotNull(entity.createTime), requireNotNull(entity.updateTime),
    )

    private fun toEntity(row: ResultRow): CodegenColumnDO = CodegenColumnDO().apply {
        id = row[CodegenColumnTable.id]
        tableId = row[CodegenColumnTable.tableId]
        columnName = row[CodegenColumnTable.columnName]
        dataType = row[CodegenColumnTable.dataType]
        columnComment = row[CodegenColumnTable.columnComment]
        nullable = row[CodegenColumnTable.nullable]
        primaryKey = row[CodegenColumnTable.primaryKeyFlag]
        ordinalPosition = row[CodegenColumnTable.ordinalPosition]
        javaType = row[CodegenColumnTable.javaType]
        javaField = row[CodegenColumnTable.javaField]
        dictType = row[CodegenColumnTable.dictType]
        example = row[CodegenColumnTable.example]
        createOperation = row[CodegenColumnTable.createOperation]
        updateOperation = row[CodegenColumnTable.updateOperation]
        listOperation = row[CodegenColumnTable.listOperation]
        listOperationCondition = row[CodegenColumnTable.listOperationCondition]
        listOperationResult = row[CodegenColumnTable.listOperationResult]
        htmlType = row[CodegenColumnTable.htmlType]
        creator = row[CodegenColumnTable.creator]
        createTime = row[CodegenColumnTable.createTime]
        updater = row[CodegenColumnTable.updater]
        updateTime = row[CodegenColumnTable.updateTime]
        deleted = row[CodegenColumnTable.deleted]
    }
}
