package im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant

import cn.idev.excel.annotation.ExcelIgnoreUnannotated
import cn.idev.excel.annotation.ExcelProperty
import im.hikaru.ruoyi.framework.excel.core.annotations.DictFormat
import im.hikaru.ruoyi.framework.excel.core.convert.DictConvert
import im.hikaru.ruoyi.module.system.enums.DictTypeConstants
import java.time.LocalDateTime

@ExcelIgnoreUnannotated
class TenantRespVO {
    @ExcelProperty("Tenant id") var id: Long? = null
    @ExcelProperty("Tenant name") var name: String? = null
    @ExcelProperty("Contact") var contactName: String? = null
    @ExcelProperty("Mobile") var contactMobile: String? = null
    @ExcelProperty("Status", converter = DictConvert::class)
    @field:DictFormat(DictTypeConstants.COMMON_STATUS)
    var status: Int? = null
    var websites: List<String>? = null
    var packageId: Long? = null
    var expireTime: LocalDateTime? = null
    var accountCount: Int? = null
    @ExcelProperty("Created at") var createTime: LocalDateTime? = null
}
