package im.hikaru.ruoyi.module.mp.convert.message

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyBaseVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyCreateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyUpdateReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpAutoReplyDO
import im.hikaru.ruoyi.module.mp.service.message.bo.MpMessageSendOutReqBO
import kotlinx.datetime.toJavaLocalDateTime

object MpAutoReplyConvert {
    fun convert(openid: String, reply: MpAutoReplyDO): MpMessageSendOutReqBO = MpMessageSendOutReqBO().apply {
        appId = reply.appId
        this.openid = openid
        type = reply.responseMessageType
        content = reply.responseContent
        mediaId = reply.responseMediaId
        thumbMediaId = reply.responseThumbMediaId
        title = reply.responseTitle
        description = reply.responseDescription
        articles = reply.responseArticles
        musicUrl = reply.responseMusicUrl
        hqMusicUrl = reply.responseHqMusicUrl
    }

    fun convert(bean: MpAutoReplyCreateReqVO): MpAutoReplyDO = copyBase(bean, MpAutoReplyDO()).apply {
        accountId = bean.accountId
    }

    fun convert(bean: MpAutoReplyUpdateReqVO): MpAutoReplyDO = copyBase(bean, MpAutoReplyDO()).apply {
        id = bean.id
    }

    fun convert(bean: MpAutoReplyDO): MpAutoReplyRespVO = MpAutoReplyRespVO().apply {
        id = bean.id
        accountId = bean.accountId
        appId = bean.appId
        type = bean.type
        requestKeyword = bean.requestKeyword
        requestMatch = bean.requestMatch
        requestMessageType = bean.requestMessageType
        responseMessageType = bean.responseMessageType
        responseContent = bean.responseContent
        responseMediaId = bean.responseMediaId
        responseMediaUrl = bean.responseMediaUrl
        responseThumbMediaId = bean.responseThumbMediaId
        responseThumbMediaUrl = bean.responseThumbMediaUrl
        responseTitle = bean.responseTitle
        responseDescription = bean.responseDescription
        responseArticles = bean.responseArticles
        responseMusicUrl = bean.responseMusicUrl
        responseHqMusicUrl = bean.responseHqMusicUrl
        createTime = bean.createTime?.toJavaLocalDateTime()
    }

    fun convertPage(page: PageResult<MpAutoReplyDO>) = PageResult(
        total = page.total,
        list = page.list.map(::convert),
    )

    private fun copyBase(source: MpAutoReplyBaseVO, target: MpAutoReplyDO) = target.apply {
        type = source.type
        requestKeyword = source.requestKeyword
        requestMatch = source.requestMatch
        requestMessageType = source.requestMessageType
        responseMessageType = source.responseMessageType
        responseContent = source.responseContent
        responseMediaId = source.responseMediaId
        responseMediaUrl = source.responseMediaUrl
        responseThumbMediaId = source.responseThumbMediaId
        responseThumbMediaUrl = source.responseThumbMediaUrl
        responseTitle = source.responseTitle
        responseDescription = source.responseDescription
        responseArticles = source.responseArticles
        responseMusicUrl = source.responseMusicUrl
        responseHqMusicUrl = source.responseHqMusicUrl
    }
}
