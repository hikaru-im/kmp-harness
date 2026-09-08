package im.hikaru.ruoyi.framework.mybatis.core.dataobject

import kotlinx.datetime.LocalDateTime
import im.hikaru.ruoyi.framework.mybatis.core.type.smallIntBoolean
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.core.Table

/**
 * 基础实体表 (迁移自 MyBatis-Plus BaseDO)
 *
 * 迁移说明：
 *  - MyBatis-Plus 的 `@TableField(fill = FieldFill.INSERT)` 自动填充 → 由 [im.hikaru.ruoyi.framework.mybatis.core.handler.DefaultDBFieldHandler] 在 insert/update 时填充
 *  - `@TableLogic` 逻辑删除 → 通过 [deleted] 字段 + DAO 层查询条件实现
 *  - 审计字段 (createTime/updateTime/creator/updater) 统一定义在此
 *
 * 所有业务表应继承本类，复用审计字段定义：
 * ```
 * object UserTable : BaseTable("system_user") {
 *     val name = varchar("name", 64)
 *     ...
 * }
 * ```
 *
 * @author 芋道源码
 */
abstract class BaseTable(name: String) : Table(name) {

    /** 创建时间 */
    val createTime = datetime("create_time")
    /** 最后更新时间 */
    val updateTime = datetime("update_time")
    /** 创建者 (userId 字符串) */
    val creator = varchar("creator", 64).nullable()
    /** 更新者 (userId 字符串) */
    val updater = varchar("updater", 64).nullable()
    /** 是否删除 (逻辑删除标记) */
    val deleted = smallIntBoolean("deleted").default(false)
}
