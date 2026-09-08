package im.hikaru.ruoyi.module.system.convert.tenant

import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserSaveReqVO

object TenantConvert {
    fun convert02(bean: TenantSaveReqVO): UserSaveReqVO = UserSaveReqVO().apply {
        username = bean.username
        password = bean.password
        nickname = bean.contactName
        mobile = bean.contactMobile
    }
}
