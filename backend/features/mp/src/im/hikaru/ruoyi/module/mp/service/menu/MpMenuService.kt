package im.hikaru.ruoyi.module.mp.service.menu

import im.hikaru.ruoyi.module.mp.controller.admin.menu.vo.MpMenuSaveReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.menu.MpMenuDO
import jakarta.validation.Valid
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage

interface MpMenuService {
    fun saveMenu(@Valid createReqVO: MpMenuSaveReqVO): Unit
    fun deleteMenuByAccountId(accountId: Long): Unit
    fun reply(appId: String, key: String, openid: String): WxMpXmlOutMessage?
    fun getMenuListByAccountId(accountId: Long): List<MpMenuDO>
}
