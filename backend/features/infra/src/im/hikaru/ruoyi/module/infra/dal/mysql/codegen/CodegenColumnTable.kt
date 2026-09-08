package im.hikaru.ruoyi.module.infra.dal.mysql.codegen

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object CodegenColumnTable : BaseTable("infra_codegen_column") {
    val id = long("id").autoIncrement("infra_codegen_column_seq")
    val tableId = long("table_id")
    val columnName = varchar("column_name", 200)
    val dataType = varchar("data_type", 100)
    val columnComment = varchar("column_comment", 500)
    val nullable = bool("nullable")
    val primaryKeyFlag = bool("primary_key")
    val ordinalPosition = integer("ordinal_position")
    val javaType = varchar("java_type", 32)
    val javaField = varchar("java_field", 64)
    val dictType = varchar("dict_type", 200).nullable()
    val example = varchar("example", 64).nullable()
    val createOperation = bool("create_operation")
    val updateOperation = bool("update_operation")
    val listOperation = bool("list_operation")
    val listOperationCondition = varchar("list_operation_condition", 32)
    val listOperationResult = bool("list_operation_result")
    val htmlType = varchar("html_type", 32)
    override val primaryKey = PrimaryKey(id)
}
