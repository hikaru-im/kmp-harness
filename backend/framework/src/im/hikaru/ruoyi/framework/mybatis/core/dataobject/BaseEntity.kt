package im.hikaru.ruoyi.framework.mybatis.core.dataobject

import kotlinx.datetime.LocalDateTime

/**
 * 基础实体数据类接口 (对应 MyBatis-Plus 的 BaseDO POJO)
 *
 * 业务实体实现本接口以持有审计字段。
 * 与 [BaseTable] 的列一一对应，用于 DAO 查询结果映射。
 *
 * @author 芋道源码
 */
interface BaseEntity {
    var createTime: LocalDateTime?
    var updateTime: LocalDateTime?
    var creator: String?
    var updater: String?
    var deleted: Boolean

    /**
     * 把 creator、createTime、updateTime、updater 都清空，
     * 避免前端直接传递 creator 之类的字段，直接就被更新了
     */
    fun clean() {
        createTime = null
        updateTime = null
        creator = null
        updater = null
    }
}
