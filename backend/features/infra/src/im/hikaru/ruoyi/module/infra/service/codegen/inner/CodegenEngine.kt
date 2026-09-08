package im.hikaru.ruoyi.module.infra.service.codegen.inner

import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenColumnDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.codegen.CodegenTableDO
import org.springframework.stereotype.Component

@Component
class CodegenEngine {

    fun execute(
        table: CodegenTableDO,
        columns: List<CodegenColumnDO>,
        subTables: List<CodegenTableDO> = emptyList(),
        subColumnsList: List<List<CodegenColumnDO>> = emptyList(),
    ): Map<String, String> {
        val module = requireNotNull(table.moduleName)
        val business = requireNotNull(table.businessName)
        val className = requireNotNull(table.className)
        val tableName = requireNotNull(table.tableName)
        val rootPackage = "im.hikaru.ruoyi.module.$module"
        val rootPath = "src/im/hikaru/ruoyi/module/$module"
        return linkedMapOf<String, String>().apply {
            this["$rootPath/dal/dataobject/$business/${className}DO.kt"] = renderDo(rootPackage, business, className, columns)
            this["$rootPath/dal/mysql/$business/${className}Table.kt"] =
                renderTable(rootPackage, business, className, tableName, columns)
            this["$rootPath/dal/mysql/$business/${className}Dao.kt"] =
                renderDao(rootPackage, business, className, columns)
            this["$rootPath/service/$business/${className}Service.kt"] =
                renderService(rootPackage, business, className)
            this["$rootPath/controller/admin/$business/${className}Controller.kt"] =
                renderController(rootPackage, business, className)
            this["$rootPath/controller/admin/$business/vo/${className}SaveReqVO.kt"] =
                renderSaveVo(rootPackage, business, className, columns)
            this["$rootPath/controller/admin/$business/vo/${className}RespVO.kt"] =
                renderRespVo(rootPackage, business, className, columns)
            this["$rootPath/api/$business.ts"] = renderApi(className, columns)
            if (subTables.isNotEmpty()) {
                this["README-${className}-relations.txt"] = subTables.zip(subColumnsList).joinToString("\n") { (sub, cols) ->
                    "${sub.tableName}: ${cols.joinToString { it.columnName.orEmpty() }}"
                }
            }
        }
    }

    private fun renderDo(root: String, business: String, className: String, columns: List<CodegenColumnDO>): String =
        buildString {
            appendLine("package $root.dal.dataobject.$business")
            appendLine()
            appendLine("import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity")
            appendLine("import kotlinx.datetime.LocalDateTime")
            appendLine()
            appendLine("class ${className}DO : BaseEntity {")
            domainColumns(columns).forEach { appendLine("    var ${safeIdentifier(it.javaField)}: ${typeOf(it)}? = null") }
            appendLine("    override var createTime: LocalDateTime? = null")
            appendLine("    override var updateTime: LocalDateTime? = null")
            appendLine("    override var creator: String? = null")
            appendLine("    override var updater: String? = null")
            appendLine("    override var deleted: Boolean = false")
            appendLine("}")
        }

    private fun renderTable(
        root: String,
        business: String,
        className: String,
        tableName: String,
        columns: List<CodegenColumnDO>,
    ): String = buildString {
        appendLine("package $root.dal.mysql.$business")
        appendLine()
        appendLine("import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable")
        appendLine("import org.jetbrains.exposed.v1.datetime.datetime")
        appendLine()
        appendLine("object ${className}Table : BaseTable(\"$tableName\") {")
        val idColumn = columns.firstOrNull { it.primaryKey == true }
        appendLine("    val id = ${idColumn?.let(::exposedColumnWithoutNullable) ?: "long(\"id\")"}.autoIncrement(\"${tableName}_seq\")")
        tableColumns(columns).filterNot { it.primaryKey == true }.forEach {
            appendLine("    val ${safeIdentifier(it.javaField)} = ${exposedColumn(it)}")
        }
        appendLine("    override val primaryKey = PrimaryKey(id)")
        appendLine("}")
    }

    private fun renderDao(root: String, business: String, className: String, columns: List<CodegenColumnDO>): String =
        buildString {
            appendLine("package $root.dal.mysql.$business")
            appendLine()
            appendLine("import $root.dal.dataobject.$business.${className}DO")
            appendLine("import org.jetbrains.exposed.v1.core.ResultRow")
            appendLine("import org.jetbrains.exposed.v1.core.eq")
            appendLine("import im.hikaru.ruoyi.framework.mybatis.core.mapper.deleteWhere")
            appendLine("import im.hikaru.ruoyi.framework.mybatis.core.mapper.selectAll")
            appendLine("import org.jetbrains.exposed.v1.jdbc.transactions.transaction")
            appendLine()
            appendLine("object ${className}Dao {")
            appendLine("    fun selectById(id: Long): ${className}DO? = transaction {")
            appendLine("        ${className}Table.selectAll().where { ${className}Table.id eq id }")
            appendLine("            .singleOrNull()?.let(::toEntity)")
            appendLine("    }")
            appendLine()
            appendLine("    fun deleteById(id: Long): Int = transaction {")
            appendLine("        ${className}Table.deleteWhere { ${className}Table.id eq id }")
            appendLine("    }")
            appendLine()
            appendLine("    private fun toEntity(row: ResultRow): ${className}DO = ${className}DO().apply {")
            domainColumns(columns).forEach { column ->
                val tableField = if (column.primaryKey == true) "id" else safeIdentifier(column.javaField)
                appendLine("        ${safeIdentifier(column.javaField)} = row[${className}Table.$tableField]")
            }
            appendLine("        creator = row[${className}Table.creator]")
            appendLine("        createTime = row[${className}Table.createTime]")
            appendLine("        updater = row[${className}Table.updater]")
            appendLine("        updateTime = row[${className}Table.updateTime]")
            appendLine("        deleted = row[${className}Table.deleted]")
            appendLine("    }")
            appendLine("}")
        }

    private fun renderService(root: String, business: String, className: String): String = """
        package $root.service.$business

        import $root.dal.dataobject.$business.${className}DO

        interface ${className}Service {
            fun get${className}(id: Long): ${className}DO?
            fun delete${className}(id: Long)
        }
    """.trimIndent()

    private fun renderController(root: String, business: String, className: String): String = """
        package $root.controller.admin.$business

        import im.hikaru.ruoyi.framework.common.pojo.CommonResult
        import $root.service.$business.${className}Service
        import org.springframework.web.bind.annotation.GetMapping
        import org.springframework.web.bind.annotation.RequestMapping
        import org.springframework.web.bind.annotation.RequestParam
        import org.springframework.web.bind.annotation.RestController

        @RestController
        @RequestMapping("/$business")
        class ${className}Controller(private val service: ${className}Service) {
            @GetMapping("/get")
            fun get(@RequestParam id: Long) = CommonResult.success(service.get${className}(id))
        }
    """.trimIndent()

    private fun renderSaveVo(
        root: String,
        business: String,
        className: String,
        columns: List<CodegenColumnDO>,
    ): String = buildString {
        appendLine("package $root.controller.admin.$business.vo")
        appendLine()
        appendLine("class ${className}SaveReqVO {")
        columns.filter { it.createOperation == true || it.updateOperation == true }.forEach {
            appendLine("    var ${safeIdentifier(it.javaField)}: ${typeOf(it)}? = null")
        }
        appendLine("}")
    }

    private fun renderRespVo(
        root: String,
        business: String,
        className: String,
        columns: List<CodegenColumnDO>,
    ): String = buildString {
        appendLine("package $root.controller.admin.$business.vo")
        appendLine()
        appendLine("class ${className}RespVO {")
        columns.filter { it.listOperationResult == true }.forEach {
            appendLine("    var ${safeIdentifier(it.javaField)}: ${typeOf(it)}? = null")
        }
        appendLine("}")
    }

    private fun renderApi(className: String, columns: List<CodegenColumnDO>): String = """
        export interface ${className}Resp {
        ${columns.filter { it.listOperationResult == true }.joinToString("\n") { "  ${safeIdentifier(it.javaField)}?: ${tsType(it)}" }}
        }
    """.trimIndent()

    private fun domainColumns(columns: List<CodegenColumnDO>): List<CodegenColumnDO> =
        columns.filterNot { it.javaField in CodegenBuilder.BASE_ENTITY_FIELDS }

    private fun tableColumns(columns: List<CodegenColumnDO>): List<CodegenColumnDO> =
        columns.filterNot {
            it.javaField in CodegenBuilder.BASE_ENTITY_FIELDS &&
                it.javaField != CodegenBuilder.TENANT_ID_FIELD
        }

    private fun typeOf(column: CodegenColumnDO): String = when (column.javaType) {
        "Int", "Long", "Boolean", "Float", "Double", "ByteArray" -> requireNotNull(column.javaType)
        "BigDecimal" -> "java.math.BigDecimal"
        "LocalDateTime" -> "kotlinx.datetime.LocalDateTime"
        else -> "String"
    }

    private fun tsType(column: CodegenColumnDO): String = when (column.javaType) {
        "Int", "Long", "Float", "Double", "BigDecimal" -> "number"
        "Boolean" -> "boolean"
        else -> "string"
    }

    private fun exposedColumn(column: CodegenColumnDO): String =
        exposedColumnWithoutNullable(column) + if (column.nullable == true) ".nullable()" else ""

    private fun exposedColumnWithoutNullable(column: CodegenColumnDO): String = when (column.javaType) {
        "Long" -> "long(\"${column.columnName}\")"
        "Int" -> "integer(\"${column.columnName}\")"
        "Boolean" -> "bool(\"${column.columnName}\")"
        "BigDecimal" -> "decimal(\"${column.columnName}\", 18, 6)"
        "Float" -> "float(\"${column.columnName}\")"
        "Double" -> "double(\"${column.columnName}\")"
        "ByteArray" -> "binary(\"${column.columnName}\")"
        "LocalDateTime" -> "datetime(\"${column.columnName}\")"
        else -> "varchar(\"${column.columnName}\", 255)"
    }

    private fun safeIdentifier(name: String?): String {
        val value = name.orEmpty().ifEmpty { "field" }
        return if (value in KOTLIN_KEYWORDS) "`$value`" else value
    }

    companion object {
        private val KOTLIN_KEYWORDS = setOf("class", "object", "when", "is", "in", "as", "val", "var", "fun")
    }
}
