package im.hikaru.ruoyi.module.infra.service.codegen

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.CodegenCreateListReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.CodegenUpdateReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.column.CodegenColumnSaveReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.CodegenTablePageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.CodegenTableSaveReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.codegen.vo.table.DatabaseTableRespVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenColumnDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenTableDO
import im.hikaru.ruoyi.module.infra.dal.mysql.codegen.CodegenColumnDao
import im.hikaru.ruoyi.module.infra.dal.mysql.codegen.CodegenTableDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_COLUMN_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_IMPORT_COLUMNS_NULL
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_IMPORT_TABLE_NULL
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_MASTER_GENERATION_FAIL_NO_SUB_TABLE
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_MASTER_TABLE_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_SUB_COLUMN_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_SYNC_NONE_CHANGE
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_TABLE_EXISTS
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_TABLE_INFO_COLUMN_COMMENT_IS_NULL
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_TABLE_INFO_TABLE_COMMENT_IS_NULL
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.CODEGEN_TABLE_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenSceneEnum
import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenTemplateTypeEnum
import im.hikaru.ruoyi.module.infra.framework.codegen.config.CodegenProperties
import im.hikaru.ruoyi.module.infra.service.codegen.inner.CodegenBuilder
import im.hikaru.ruoyi.module.infra.service.codegen.inner.CodegenEngine
import im.hikaru.ruoyi.module.infra.service.db.DatabaseColumnInfo
import im.hikaru.ruoyi.module.infra.service.db.DatabaseTableInfo
import im.hikaru.ruoyi.module.infra.service.db.DatabaseTableService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CodegenServiceImpl(
    private val databaseTableService: DatabaseTableService,
    private val codegenBuilder: CodegenBuilder,
    private val codegenEngine: CodegenEngine,
    private val codegenProperties: CodegenProperties,
) : CodegenService {

    @Transactional(rollbackFor = [Exception::class])
    override fun createCodegenList(author: String, reqVO: CodegenCreateListReqVO): List<Long> =
        requireNotNull(reqVO.tableNames).map { createCodegen(author, requireNotNull(reqVO.dataSourceConfigId), it) }

    private fun createCodegen(author: String, dataSourceConfigId: Long, tableName: String): Long {
        val tableInfo = databaseTableService.getTable(dataSourceConfigId, tableName)
        validateTableInfo(tableInfo)
        tableInfo!!
        if (CodegenTableDao.selectByTableNameAndDataSourceConfigId(tableInfo.name, dataSourceConfigId) != null) {
            throw exception(CODEGEN_TABLE_EXISTS)
        }
        val table = codegenBuilder.buildTable(tableInfo).apply {
            this.dataSourceConfigId = dataSourceConfigId
            scene = CodegenSceneEnum.ADMIN.scene
            frontType = codegenProperties.frontType
            this.author = author
        }
        val tableId = CodegenTableDao.insert(table)
        val columns = codegenBuilder.buildColumns(tableId, tableInfo.columns)
        if (columns.none { it.primaryKey == true } && columns.isNotEmpty()) {
            columns.first().apply {
                primaryKey = true
                createOperation = false
                updateOperation = true
                listOperation = false
            }
        }
        CodegenColumnDao.insertBatch(columns)
        return tableId
    }

    internal fun validateTableInfo(tableInfo: DatabaseTableInfo?) {
        if (tableInfo == null) throw exception(CODEGEN_IMPORT_TABLE_NULL)
        if (tableInfo.comment.isBlank()) throw exception(CODEGEN_TABLE_INFO_TABLE_COMMENT_IS_NULL)
        if (tableInfo.columns.isEmpty()) throw exception(CODEGEN_IMPORT_COLUMNS_NULL)
        tableInfo.columns.firstOrNull { it.comment.isBlank() }?.let {
            throw exception(CODEGEN_TABLE_INFO_COLUMN_COMMENT_IS_NULL, it.name)
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun updateCodegen(updateReqVO: CodegenUpdateReqVO) {
        val tableReq = requireNotNull(updateReqVO.table)
        val tableId = requireNotNull(tableReq.id)
        if (CodegenTableDao.selectById(tableId) == null) throw exception(CODEGEN_TABLE_NOT_EXISTS)
        if (tableReq.templateType == CodegenTemplateTypeEnum.SUB.type) {
            if (CodegenTableDao.selectById(requireNotNull(tableReq.masterTableId)) == null) {
                throw exception(CODEGEN_MASTER_TABLE_NOT_EXISTS, tableReq.masterTableId)
            }
            if (requireNotNull(updateReqVO.columns).none { it.id == tableReq.subJoinColumnId }) {
                throw exception(CODEGEN_SUB_COLUMN_NOT_EXISTS, tableReq.subJoinColumnId)
            }
        }
        CodegenTableDao.updateById(tableReq.toEntity())
        requireNotNull(updateReqVO.columns).forEach { CodegenColumnDao.updateById(it.toEntity()) }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun syncCodegenFromDB(tableId: Long) {
        val table = CodegenTableDao.selectById(tableId) ?: throw exception(CODEGEN_TABLE_NOT_EXISTS)
        val tableInfo = databaseTableService.getTable(requireNotNull(table.dataSourceConfigId), requireNotNull(table.tableName))
        validateTableInfo(tableInfo)
        syncColumns(tableId, tableInfo!!.columns)
    }

    private fun syncColumns(tableId: Long, databaseColumns: List<DatabaseColumnInfo>) {
        val existing = CodegenColumnDao.selectListByTableId(tableId)
        val existingByName = existing.associateBy { it.columnName }
        val databaseByName = databaseColumns.associateBy(DatabaseColumnInfo::name)
        val changedNames = databaseColumns.filter { column ->
            val old = existingByName[column.name] ?: return@filter true
            old.dataType != column.dataType || old.nullable != column.nullable || old.primaryKey != column.primaryKey ||
                old.columnComment != codegenBuilder.sanitizeComment(column.comment) ||
                old.ordinalPosition != column.ordinalPosition
        }.mapTo(mutableSetOf(), DatabaseColumnInfo::name)
        val deleteIds = existing.filter { it.columnName !in databaseByName || it.columnName in changedNames }
            .mapNotNull(CodegenColumnDO::id)
        val insertColumns = databaseColumns.filter { it.name !in existingByName || it.name in changedNames }
        if (deleteIds.isEmpty() && insertColumns.isEmpty()) throw exception(CODEGEN_SYNC_NONE_CHANGE)
        CodegenColumnDao.deleteByIds(deleteIds)
        CodegenColumnDao.insertBatch(codegenBuilder.buildColumns(tableId, insertColumns))
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun deleteCodegen(tableId: Long) {
        if (CodegenTableDao.selectById(tableId) == null) throw exception(CODEGEN_TABLE_NOT_EXISTS)
        CodegenTableDao.deleteById(tableId)
        CodegenColumnDao.deleteListByTableId(tableId)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun deleteCodegenList(tableIds: List<Long>) {
        CodegenTableDao.deleteByIds(tableIds)
        CodegenColumnDao.deleteListByTableIds(tableIds)
    }

    override fun getCodegenTableList(dataSourceConfigId: Long): List<CodegenTableDO> =
        CodegenTableDao.selectListByDataSourceConfigId(dataSourceConfigId)

    override fun getCodegenTablePage(pageReqVO: CodegenTablePageReqVO): PageResult<CodegenTableDO> =
        CodegenTableDao.selectPage(pageReqVO)

    override fun getCodegenTable(id: Long): CodegenTableDO? = CodegenTableDao.selectById(id)

    override fun getCodegenColumnListByTableId(tableId: Long): List<CodegenColumnDO> =
        CodegenColumnDao.selectListByTableId(tableId)

    override fun generationCodes(tableId: Long): Map<String, String> {
        val table = CodegenTableDao.selectById(tableId) ?: throw exception(CODEGEN_TABLE_NOT_EXISTS)
        val columns = CodegenColumnDao.selectListByTableId(tableId)
        if (columns.isEmpty()) throw exception(CODEGEN_COLUMN_NOT_EXISTS)
        var subTables = emptyList<CodegenTableDO>()
        var subColumns = emptyList<List<CodegenColumnDO>>()
        if (CodegenTemplateTypeEnum.isMaster(table.templateType)) {
            subTables = CodegenTableDao.selectListByTemplateTypeAndMasterTableId(CodegenTemplateTypeEnum.SUB.type, tableId)
            if (subTables.isEmpty()) throw exception(CODEGEN_MASTER_GENERATION_FAIL_NO_SUB_TABLE)
            subColumns = subTables.map { subTable ->
                CodegenColumnDao.selectListByTableId(requireNotNull(subTable.id)).also { list ->
                    if (list.none { it.id == subTable.subJoinColumnId }) {
                        throw exception(CODEGEN_SUB_COLUMN_NOT_EXISTS, subTable.subJoinColumnId)
                    }
                }
            }
        }
        return codegenEngine.execute(table, columns, subTables, subColumns)
    }

    override fun getDatabaseTableList(
        dataSourceConfigId: Long,
        name: String?,
        comment: String?,
    ): List<DatabaseTableRespVO> {
        val existingNames = CodegenTableDao.selectListByDataSourceConfigId(dataSourceConfigId)
            .mapNotNull(CodegenTableDO::tableName).toSet()
        return databaseTableService.getTableList(dataSourceConfigId, name, comment)
            .filterNot { it.name in existingNames }
            .map { table -> DatabaseTableRespVO().apply { this.name = table.name; this.comment = table.comment } }
    }

    private fun CodegenTableSaveReqVO.toEntity(): CodegenTableDO = CodegenTableDO().also { target ->
        target.id = id; target.scene = scene; target.tableName = tableName; target.tableComment = tableComment
        target.remark = remark; target.moduleName = moduleName; target.businessName = businessName
        target.className = className; target.classComment = classComment; target.author = author
        target.templateType = templateType; target.frontType = frontType; target.parentMenuId = parentMenuId
        target.masterTableId = masterTableId; target.subJoinColumnId = subJoinColumnId; target.subJoinMany = subJoinMany
        target.treeParentColumnId = treeParentColumnId; target.treeNameColumnId = treeNameColumnId
    }

    private fun CodegenColumnSaveReqVO.toEntity(): CodegenColumnDO = CodegenColumnDO().also { target ->
        target.id = id; target.tableId = tableId; target.columnName = columnName; target.dataType = dataType
        target.columnComment = columnComment; target.nullable = nullable; target.primaryKey = primaryKey
        target.ordinalPosition = ordinalPosition; target.javaType = javaType; target.javaField = javaField
        target.dictType = dictType; target.example = example; target.createOperation = createOperation
        target.updateOperation = updateOperation; target.listOperation = listOperation
        target.listOperationCondition = listOperationCondition; target.listOperationResult = listOperationResult
        target.htmlType = htmlType
    }
}
