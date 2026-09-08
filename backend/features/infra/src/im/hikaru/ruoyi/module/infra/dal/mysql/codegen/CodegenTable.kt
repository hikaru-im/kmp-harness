package im.hikaru.ruoyi.module.infra.dal.mysql.codegen

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object CodegenTable : BaseTable("infra_codegen_table") {
    val id = long("id").autoIncrement("infra_codegen_table_seq")
    val dataSourceConfigId = long("data_source_config_id")
    val scene = integer("scene")
    val tableNameColumn = varchar("table_name", 200)
    val tableComment = varchar("table_comment", 500)
    val remark = varchar("remark", 500).nullable()
    val moduleName = varchar("module_name", 30)
    val businessName = varchar("business_name", 30)
    val className = varchar("class_name", 100)
    val classComment = varchar("class_comment", 50)
    val author = varchar("author", 50)
    val templateType = integer("template_type")
    val frontType = integer("front_type")
    val parentMenuId = long("parent_menu_id").nullable()
    val masterTableId = long("master_table_id").nullable()
    val subJoinColumnId = long("sub_join_column_id").nullable()
    val subJoinMany = bool("sub_join_many").nullable()
    val treeParentColumnId = long("tree_parent_column_id").nullable()
    val treeNameColumnId = long("tree_name_column_id").nullable()
    override val primaryKey = PrimaryKey(id)
}
