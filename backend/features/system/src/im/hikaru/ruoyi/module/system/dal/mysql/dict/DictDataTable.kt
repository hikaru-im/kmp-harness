package im.hikaru.ruoyi.module.system.dal.mysql.dict

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable

object DictDataTable : BaseTable("system_dict_data") {
    val id = long("id").autoIncrement("system_dict_data_seq")
    val sort = integer("sort")
    val label = varchar("label", 100)
    val value = varchar("value", 100)
    val dictType = varchar("dict_type", 100)
    val status = integer("status")
    val colorType = varchar("color_type", 100).nullable()
    val cssClass = varchar("css_class", 100).nullable()
    val remark = varchar("remark", 500).nullable()
    override val primaryKey = PrimaryKey(id)
}
