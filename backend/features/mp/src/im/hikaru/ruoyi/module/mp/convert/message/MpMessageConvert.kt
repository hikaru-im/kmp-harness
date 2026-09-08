package im.hikaru.ruoyi.module.mp.convert.message

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessageRespVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessageSendReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.account.MpAccountDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageDO
import im.hikaru.ruoyi.module.mp.dal.dataobject.user.MpUserDO
import im.hikaru.ruoyi.module.mp.service.message.bo.MpMessageSendOutReqBO
import kotlinx.datetime.toJavaLocalDateTime
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutNewsMessage

object MpMessageConvert {
    fun convert(wxMessage: WxMpXmlMessage, account: MpAccountDO, user: MpUserDO): MpMessageDO = MpMessageDO().apply {
        msgId = wxMessage.msgId
        accountId = account.id
        appId = account.appId
        userId = user.id
        openid = user.openid
        type = wxMessage.msgType
        content = wxMessage.content
        mediaId = wxMessage.mediaId
        recognition = wxMessage.recognition
        format = wxMessage.format
        title = wxMessage.title
        description = wxMessage.description
        thumbMediaId = wxMessage.thumbMediaId
        url = wxMessage.url
        locationX = wxMessage.locationX
        locationY = wxMessage.locationY
        scale = wxMessage.scale
        label = wxMessage.label
        event = wxMessage.event
        eventKey = wxMessage.eventKey
    }

    fun convert(send: MpMessageSendOutReqBO, account: MpAccountDO, user: MpUserDO): MpMessageDO =
        populateMessage(
            type = requireNotNull(send.type),
            content = send.content,
            mediaId = send.mediaId,
            title = send.title,
            description = send.description,
            thumbMediaId = send.thumbMediaId,
            articles = send.articles,
            musicUrl = send.musicUrl,
            hqMusicUrl = send.hqMusicUrl,
        ).apply {
            accountId = account.id
            appId = account.appId
            userId = user.id
            openid = user.openid
        }

    fun convert(send: MpMessageSendReqVO, user: MpUserDO): WxMpKefuMessage = when (send.type) {
        WxConsts.KefuMsgType.TEXT -> WxMpKefuMessage.TEXT().content(send.content).toUser(user.openid).build()
        WxConsts.KefuMsgType.IMAGE -> WxMpKefuMessage.IMAGE().mediaId(send.mediaId).toUser(user.openid).build()
        WxConsts.KefuMsgType.VOICE -> WxMpKefuMessage.VOICE().mediaId(send.mediaId).toUser(user.openid).build()
        WxConsts.KefuMsgType.VIDEO -> WxMpKefuMessage.VIDEO().mediaId(send.mediaId)
            .title(send.title).description(send.description).toUser(user.openid).build()
        WxConsts.KefuMsgType.NEWS -> WxMpKefuMessage.NEWS().articles(send.articles.orEmpty().map(::toKefuArticle))
            .toUser(user.openid).build()
        WxConsts.KefuMsgType.MUSIC -> WxMpKefuMessage.MUSIC().title(send.title).description(send.description)
            .thumbMediaId(send.thumbMediaId).musicUrl(send.musicUrl).hqMusicUrl(send.hqMusicUrl)
            .toUser(user.openid).build()
        else -> throw IllegalArgumentException("Unsupported WeChat message type: ${send.type}")
    }

    fun convert(wxMessage: WxMpKefuMessage, account: MpAccountDO, user: MpUserDO): MpMessageDO =
        populateMessage(
            type = requireNotNull(wxMessage.msgType),
            content = wxMessage.content,
            mediaId = wxMessage.mediaId,
            title = wxMessage.title,
            description = wxMessage.description,
            thumbMediaId = wxMessage.thumbMediaId,
            articles = wxMessage.articles.orEmpty().map { article ->
                MpMessageDO.Article().apply {
                    title = article.title
                    description = article.description
                    picUrl = article.picUrl
                    url = article.url
                }
            },
            musicUrl = wxMessage.musicUrl,
            hqMusicUrl = wxMessage.hqMusicUrl,
        ).apply {
            accountId = account.id
            appId = account.appId
            userId = user.id
            openid = user.openid
        }

    fun convertOut(message: MpMessageDO, account: MpAccountDO): WxMpXmlOutMessage = when (message.type) {
        WxConsts.XmlMsgType.TEXT -> WxMpXmlOutMessage.TEXT().content(message.content)
            .fromUser(account.account).toUser(message.openid).build()
        WxConsts.XmlMsgType.IMAGE -> WxMpXmlOutMessage.IMAGE().mediaId(message.mediaId)
            .fromUser(account.account).toUser(message.openid).build()
        WxConsts.XmlMsgType.VOICE -> WxMpXmlOutMessage.VOICE().mediaId(message.mediaId)
            .fromUser(account.account).toUser(message.openid).build()
        WxConsts.XmlMsgType.VIDEO -> WxMpXmlOutMessage.VIDEO().mediaId(message.mediaId)
            .title(message.title).description(message.description)
            .fromUser(account.account).toUser(message.openid).build()
        WxConsts.XmlMsgType.NEWS -> WxMpXmlOutMessage.NEWS().articles(message.articles.orEmpty().map(::toOutArticle))
            .fromUser(account.account).toUser(message.openid).build()
        WxConsts.XmlMsgType.MUSIC -> WxMpXmlOutMessage.MUSIC().title(message.title).description(message.description)
            .musicUrl(message.musicUrl).hqMusicUrl(message.hqMusicUrl).thumbMediaId(message.thumbMediaId)
            .fromUser(account.account).toUser(message.openid).build()
        else -> throw IllegalArgumentException("Unsupported WeChat message type: ${message.type}")
    }

    fun convert(bean: MpMessageDO): MpMessageRespVO = MpMessageRespVO().apply {
        id = bean.id?.toInt()
        msgId = bean.msgId
        accountId = bean.accountId
        appId = bean.appId
        userId = bean.userId
        openid = bean.openid
        type = bean.type
        sendFrom = bean.sendFrom
        content = bean.content
        mediaId = bean.mediaId
        mediaUrl = bean.mediaUrl
        recognition = bean.recognition
        format = bean.format
        title = bean.title
        description = bean.description
        thumbMediaId = bean.thumbMediaId
        thumbMediaUrl = bean.thumbMediaUrl
        url = bean.url
        locationX = bean.locationX
        locationY = bean.locationY
        scale = bean.scale
        label = bean.label
        articles = bean.articles
        musicUrl = bean.musicUrl
        hqMusicUrl = bean.hqMusicUrl
        event = bean.event
        eventKey = bean.eventKey
        createTime = bean.createTime?.toJavaLocalDateTime()
    }

    fun convertPage(page: PageResult<MpMessageDO>) = PageResult(
        total = page.total,
        list = page.list.map(::convert),
    )

    private fun populateMessage(
        type: String,
        content: String?,
        mediaId: String?,
        title: String?,
        description: String?,
        thumbMediaId: String?,
        articles: List<MpMessageDO.Article>?,
        musicUrl: String?,
        hqMusicUrl: String?,
    ): MpMessageDO = MpMessageDO().apply {
        this.type = type
        when (type) {
            WxConsts.XmlMsgType.TEXT -> this.content = content
            WxConsts.XmlMsgType.IMAGE, WxConsts.XmlMsgType.VOICE -> this.mediaId = mediaId
            WxConsts.XmlMsgType.VIDEO -> {
                this.mediaId = mediaId
                this.title = title
                this.description = description
            }
            WxConsts.XmlMsgType.NEWS -> this.articles = articles
            WxConsts.XmlMsgType.MUSIC -> {
                this.title = title
                this.description = description
                this.thumbMediaId = thumbMediaId
                this.musicUrl = musicUrl
                this.hqMusicUrl = hqMusicUrl
            }
            else -> throw IllegalArgumentException("Unsupported WeChat message type: $type")
        }
    }

    private fun toOutArticle(article: MpMessageDO.Article): WxMpXmlOutNewsMessage.Item =
        WxMpXmlOutNewsMessage.Item().apply {
            title = article.title
            description = article.description
            picUrl = article.picUrl
            url = article.url
        }

    private fun toKefuArticle(article: MpMessageDO.Article): WxMpKefuMessage.WxArticle =
        WxMpKefuMessage.WxArticle().apply {
            title = article.title
            description = article.description
            picUrl = article.picUrl
            url = article.url
        }
}
