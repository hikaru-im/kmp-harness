package im.hikaru.ruoyi.module.system.dal.mysql.dict

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.datetime.datetime

object DictTypeTable : BaseTable("system_dict_type") {
    val id = long("id").autoIncrement("system_dict_type_seq")
    val name = varchar("name", 100)
    val type = varchar("type", 100)
    val status = integer("status")
    val remark = varchar("remark", 500).nullable()
    val deletedTime = datetime("deleted_time").nullable()
    override val primaryKey = PrimaryKey(id)
}
