package im.hikaru.ruoyi.module.mp.service.message

import im.hikaru.ruoyi.framework.common.pojo.*
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyCreateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyUpdateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessagePageReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpAutoReplyDO
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage

interface MpAutoReplyService {
    fun getAutoReplyPage(pageVO: MpMessagePageReqVO): PageResult<MpAutoReplyDO>
    fun getAutoReply(id: Long): MpAutoReplyDO
    fun createAutoReply(createReqVO: MpAutoReplyCreateReqVO): Long
    fun updateAutoReply(updateReqVO: MpAutoReplyUpdateReqVO): Unit
    fun deleteAutoReply(id: Long): Unit
    fun replyForMessage(appId: String, wxMessage: WxMpXmlMessage): WxMpXmlOutMessage?
    fun replyForSubscribe(appId: String, wxMessage: WxMpXmlMessage): WxMpXmlOutMessage?
}
