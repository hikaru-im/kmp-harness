package im.hikaru.ruoyi.module.mp.service.message

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessagePageReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessageSendReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import im.hikaru.ruoyi.module.mp.service.message.bo.MpMessageSendOutReqBO
import jakarta.validation.Valid
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage

interface MpMessageService {
    fun getMessagePage(pageReqVO: MpMessagePageReqVO): PageResult<MpMessageDO>
    fun receiveMessage(weixinService: WxMpService, appId: String, wxMessage: WxMpXmlMessage): Unit
    fun sendOutMessage(@Valid sendReqBO: MpMessageSendOutReqBO): WxMpXmlOutMessage
    fun sendKefuMessage(sendReqVO: MpMessageSendReqVO): MpMessageDO
}
