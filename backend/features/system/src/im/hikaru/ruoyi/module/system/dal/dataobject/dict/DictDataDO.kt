package im.hikaru.ruoyi.module.system.dal.dataobject.dict

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import kotlinx.datetime.LocalDateTime

class DictDataDO : BaseEntity {
    var id: Long? = null
    var sort: Int? = null
    var label: String? = null
    var value: String? = null
    var dictType: String? = null
    var status: Int? = null
    var colorType: String? = null
    var cssClass: String? = null
    var remark: String? = null
    override var creator: String? = null
    override var createTime: LocalDateTime? = null
    override var updater: String? = null
    override var updateTime: LocalDateTime? = null
    override var deleted: Boolean = false
}
