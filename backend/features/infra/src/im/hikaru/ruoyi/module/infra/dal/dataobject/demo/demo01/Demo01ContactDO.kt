package im.hikaru.ruoyi.module.infra.dal.dataobject.demo.demo01

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class Demo01ContactDO : BaseEntity {
    var id: Long? = null
    var name: String? = null
    var sex: Int? = null
    var birthday: LocalDateTime? = null
    var description: String? = null
    var avatar: String? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}
