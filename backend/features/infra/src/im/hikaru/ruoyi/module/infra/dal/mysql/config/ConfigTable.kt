package im.hikaru.ruoyi.module.infra.dal.mysql.config

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseTable
import org.jetbrains.exposed.v1.core.Table

/**
 * 参数配置表 (Exposed, 迁移自 MyBatis-Plus ConfigMapper + infra_config)
 *
 * @author 芋道源码
 */
object ConfigTable : BaseTable("infra_config") {

    /** 主键编号 */
    val id = long("id").autoIncrement("infra_config_seq")

    /** 参数分组 */
    val category = varchar("category", 50)

    /** 参数名称 */
    val name = varchar("name", 100)

    /** 参数键名 */
    val key = varchar("config_key", 100)

    /** 参数键值 */
    val value = varchar("value", 500)

    /** 参数类型 (参考 ConfigTypeEnum: 1-系统, 2-自定义) */
    val type = integer("type")

    /** 是否可见 */
    val visible = bool("visible")

    /** 备注 */
    val remark = varchar("remark", 500).nullable()

    override val primaryKey = PrimaryKey(id)
}
