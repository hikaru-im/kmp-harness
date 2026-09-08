package im.hikaru.ruoyi.module.system.dal.mysql.permission

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object MenuTable : BaseTable("system_menu") {
    val id = long("id").autoIncrement("system_menu_seq"); val name = varchar("name", 50); val permission = varchar("permission", 100); val type = integer("type")
    val sort = integer("sort"); val parentId = long("parent_id"); val path = varchar("path", 200).nullable(); val icon = varchar("icon", 100).nullable()
    val component = varchar("component", 255).nullable(); val componentName = varchar("component_name", 255).nullable(); val status = integer("status")
    val visible = bool("visible"); val keepAlive = bool("keep_alive"); val alwaysShow = bool("always_show")
    override val primaryKey = PrimaryKey(id)
}
