package im.hikaru.ruoyi.module.infra.service.codegen.inner

import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenColumnDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenTableDO
import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenColumnHtmlTypeEnum
import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenColumnListConditionEnum
import im.hikaru.ruoyi.module.infra.enums.codegen.CodegenTemplateTypeEnum
import im.hikaru.ruoyi.module.infra.service.db.DatabaseColumnInfo
import im.hikaru.ruoyi.module.infra.service.db.DatabaseTableInfo
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom

@Component
class CodegenBuilder {

    fun buildTable(tableInfo: DatabaseTableInfo): CodegenTableDO = CodegenTableDO().apply {
        tableName = tableInfo.name
        tableComment = sanitizeComment(tableInfo.comment)
        val nameParts = tableInfo.name.lowercase().split('_').filter(String::isNotEmpty)
        moduleName = nameParts.firstOrNull().orEmpty()
        businessName = nameParts.drop(1).ifEmpty { nameParts }.toCamelCase().replaceFirstChar(Char::lowercaseChar)
        className = nameParts.drop(1).ifEmpty { nameParts }.toCamelCase().replaceFirstChar(Char::uppercaseChar)
        classComment = sanitizeComment(tableInfo.comment)
            .removeSuffix("table")
            .removeSuffix("Table")
            .trim()
        templateType = CodegenTemplateTypeEnum.ONE.type
    }

    fun buildColumns(tableId: Long, columns: List<DatabaseColumnInfo>): List<CodegenColumnDO> =
        columns.map { column ->
            CodegenColumnDO().apply {
                this.tableId = tableId
                columnName = column.name
                dataType = column.dataType
                columnComment = sanitizeComment(column.comment)
                nullable = column.nullable
                primaryKey = column.primaryKey
                ordinalPosition = column.ordinalPosition
                javaType = column.kotlinType
                javaField = column.propertyName
                processOperations(this)
                processHtmlType(this)
                example = exampleFor(this)
            }
        }

    internal fun sanitizeComment(comment: String): String =
        comment.replace("\"", "\u201c").replace("'", "\u2019")

    private fun processOperations(column: CodegenColumnDO) {
        val field = requireNotNull(column.javaField)
        val primaryKey = column.primaryKey == true
        column.createOperation = field !in CREATE_EXCLUDED_FIELDS && !primaryKey
        column.updateOperation = field !in UPDATE_EXCLUDED_FIELDS || primaryKey
        column.listOperation = field !in LIST_EXCLUDED_FIELDS && !primaryKey
        column.listOperationCondition = LIST_CONDITION_MAPPINGS.entries
            .firstOrNull { field.endsWith(it.key, ignoreCase = true) }
            ?.value?.condition ?: CodegenColumnListConditionEnum.EQ.condition
        column.listOperationResult = field !in LIST_RESULT_EXCLUDED_FIELDS
    }

    private fun processHtmlType(column: CodegenColumnDO) {
        val field = requireNotNull(column.javaField)
        column.htmlType = HTML_TYPE_MAPPINGS.entries
            .firstOrNull { field.endsWith(it.key, ignoreCase = true) }
            ?.value?.type
        if (column.javaType == "Boolean") column.htmlType = CodegenColumnHtmlTypeEnum.RADIO.type
        if (column.javaType == "LocalDateTime") column.htmlType = CodegenColumnHtmlTypeEnum.DATETIME.type
        if (column.htmlType == null) column.htmlType = CodegenColumnHtmlTypeEnum.INPUT.type
    }

    private fun exampleFor(column: CodegenColumnDO): String? {
        val field = requireNotNull(column.javaField)
        val columnName = requireNotNull(column.columnName)
        return when {
            listOf("id", "price", "count").any { field.endsWith(it, ignoreCase = true) } ->
                ThreadLocalRandom.current().nextInt(1, Short.MAX_VALUE.toInt()).toString()
            field.endsWith("name", ignoreCase = true) -> listOf("Alice", "Bob", "Carol").random()
            listOf("status", "type").any { field.endsWith(it, ignoreCase = true) } -> listOf("1", "2").random()
            columnName.endsWith("url", ignoreCase = true) -> "https://www.iocoder.cn"
            columnName.endsWith("reason", ignoreCase = true) -> "Not applicable"
            listOf("description", "memo", "remark").any { columnName.endsWith(it, ignoreCase = true) } ->
                "Example description"
            else -> null
        }
    }

    private fun List<String>.toCamelCase(): String = joinToString("") { part ->
        part.lowercase().replaceFirstChar(Char::uppercaseChar)
    }

    companion object {
        const val TENANT_ID_FIELD = "tenantId"
        val BASE_ENTITY_FIELDS = setOf("creator", "createTime", "updater", "updateTime", "deleted", TENANT_ID_FIELD)
        private val CREATE_EXCLUDED_FIELDS = BASE_ENTITY_FIELDS + "id"
        private val UPDATE_EXCLUDED_FIELDS = BASE_ENTITY_FIELDS
        private val LIST_EXCLUDED_FIELDS = (BASE_ENTITY_FIELDS + "id") - "createTime"
        private val LIST_RESULT_EXCLUDED_FIELDS = BASE_ENTITY_FIELDS - "createTime"

        private val LIST_CONDITION_MAPPINGS = linkedMapOf(
            "name" to CodegenColumnListConditionEnum.LIKE,
            "time" to CodegenColumnListConditionEnum.BETWEEN,
            "date" to CodegenColumnListConditionEnum.BETWEEN,
        )
        private val HTML_TYPE_MAPPINGS = linkedMapOf(
            "status" to CodegenColumnHtmlTypeEnum.RADIO,
            "sex" to CodegenColumnHtmlTypeEnum.RADIO,
            "type" to CodegenColumnHtmlTypeEnum.SELECT,
            "image" to CodegenColumnHtmlTypeEnum.IMAGE_UPLOAD,
            "file" to CodegenColumnHtmlTypeEnum.FILE_UPLOAD,
            "content" to CodegenColumnHtmlTypeEnum.EDITOR,
            "description" to CodegenColumnHtmlTypeEnum.EDITOR,
            "demo" to CodegenColumnHtmlTypeEnum.EDITOR,
            "time" to CodegenColumnHtmlTypeEnum.DATETIME,
            "date" to CodegenColumnHtmlTypeEnum.DATETIME,
        )
    }
}
