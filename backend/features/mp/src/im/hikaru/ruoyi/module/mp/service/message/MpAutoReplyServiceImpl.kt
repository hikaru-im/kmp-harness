package im.hikaru.ruoyi.module.mp.service.message

import im.hikaru.ruoyi.framework.common.exception.ErrorCode
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyCreateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.autoreply.MpAutoReplyUpdateReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.message.MpMessagePageReqVO
import im.hikaru.ruoyi.module.mp.convert.message.MpAutoReplyConvert
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpAutoReplyDO
import im.hikaru.ruoyi.module.mp.dal.mysql.message.MpAutoReplyDao
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.AUTO_REPLY_ADD_KEYWORD_FAIL_EXISTS
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.AUTO_REPLY_ADD_MESSAGE_FAIL_EXISTS
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.AUTO_REPLY_ADD_SUBSCRIBE_FAIL_EXISTS
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.AUTO_REPLY_NOT_EXISTS
import im.hikaru.ruoyi.module.mp.enums.message.MpAutoReplyTypeEnum
import im.hikaru.ruoyi.module.mp.framework.mp.core.util.MpUtils
import im.hikaru.ruoyi.module.mp.service.account.MpAccountService
import jakarta.validation.Validator
import me.chanjar.weixin.common.api.WxConsts
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutMessage
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MpAutoReplyServiceImpl(
    private val mpMessageService: MpMessageService,
    private val mpAccountServiceProvider: ObjectProvider<MpAccountService>,
    private val validator: Validator,
) : MpAutoReplyService {
    private val mpAccountService: MpAccountService
        get() = mpAccountServiceProvider.getObject()
    override fun getAutoReplyPage(pageVO: MpMessagePageReqVO): PageResult<MpAutoReplyDO> = MpAutoReplyDao.selectPage(pageVO)
    override fun getAutoReply(id: Long): MpAutoReplyDO = validateAutoReplyExists(id)

    override fun createAutoReply(createReqVO: MpAutoReplyCreateReqVO): Long {
        createReqVO.responseMessageType?.let { MpUtils.validateMessage(validator, it, createReqVO) }
        val accountId = requireNotNull(createReqVO.accountId)
        validateAutoReplyConflict(null, accountId, createReqVO.type, createReqVO.requestKeyword, createReqVO.requestMessageType)
        val account = mpAccountService.getRequiredAccount(accountId)
        return MpAutoReplyDao.insert(MpAutoReplyConvert.convert(createReqVO).apply { appId = account.appId })
    }

    override fun updateAutoReply(updateReqVO: MpAutoReplyUpdateReqVO) {
        updateReqVO.responseMessageType?.let { MpUtils.validateMessage(validator, it, updateReqVO) }
        val existing = validateAutoReplyExists(requireNotNull(updateReqVO.id))
        validateAutoReplyConflict(existing.id, requireNotNull(existing.accountId), updateReqVO.type, updateReqVO.requestKeyword, updateReqVO.requestMessageType)
        MpAutoReplyDao.updateById(MpAutoReplyConvert.convert(updateReqVO))
    }

    override fun deleteAutoReply(id: Long) {
        validateAutoReplyExists(id)
        MpAutoReplyDao.deleteById(id)
    }

    override fun replyForMessage(appId: String, wxMessage: WxMpXmlMessage): WxMpXmlOutMessage? {
        var replies = if (wxMessage.msgType == WxConsts.XmlMsgType.TEXT) {
            MpAutoReplyDao.selectListByAppIdAndKeywordAll(appId, wxMessage.content.orEmpty()).ifEmpty {
                MpAutoReplyDao.selectListByAppIdAndKeywordLike(appId, wxMessage.content.orEmpty())
            }
        } else {
            emptyList()
        }
        if (replies.isEmpty()) replies = MpAutoReplyDao.selectListByAppIdAndMessage(appId, wxMessage.msgType)
        val reply = replies.firstOrNull() ?: return null
        return mpMessageService.sendOutMessage(MpAutoReplyConvert.convert(wxMessage.fromUser, reply))
    }

    override fun replyForSubscribe(appId: String, wxMessage: WxMpXmlMessage): WxMpXmlOutMessage? {
        val reply = MpAutoReplyDao.selectListByAppIdAndSubscribe(appId).firstOrNull() ?: buildDefaultSubscribeAutoReply(appId)
        return mpMessageService.sendOutMessage(MpAutoReplyConvert.convert(wxMessage.fromUser, reply))
    }

    private fun validateAutoReplyConflict(
        id: Long?,
        accountId: Long,
        type: Int?,
        keyword: String?,
        messageType: String?,
    ) {
        val existing: MpAutoReplyDO?
        val errorCode: ErrorCode?
        when (type) {
            MpAutoReplyTypeEnum.SUBSCRIBE.type -> {
                existing = MpAutoReplyDao.selectByAccountIdAndSubscribe(accountId)
                errorCode = AUTO_REPLY_ADD_SUBSCRIBE_FAIL_EXISTS
            }
            MpAutoReplyTypeEnum.MESSAGE.type -> {
                existing = MpAutoReplyDao.selectByAccountIdAndMessage(accountId, messageType)
                errorCode = AUTO_REPLY_ADD_MESSAGE_FAIL_EXISTS
            }
            MpAutoReplyTypeEnum.KEYWORD.type -> {
                existing = MpAutoReplyDao.selectByAccountIdAndKeyword(accountId, keyword)
                errorCode = AUTO_REPLY_ADD_KEYWORD_FAIL_EXISTS
            }
            else -> return
        }
        if (existing != null && (id == null || id != existing.id)) throw exception(requireNotNull(errorCode))
    }

    private fun validateAutoReplyExists(id: Long): MpAutoReplyDO =
        MpAutoReplyDao.selectById(id) ?: throw exception(AUTO_REPLY_NOT_EXISTS)

    private fun buildDefaultSubscribeAutoReply(appId: String): MpAutoReplyDO {
        val account = requireNotNull(mpAccountService.getAccountFromCache(appId)) { "Mp account($appId) does not exist" }
        return MpAutoReplyDO().apply {
            this.appId = appId
            accountId = account.id
            type = MpAutoReplyTypeEnum.SUBSCRIBE.type
            responseMessageType = WxConsts.XmlMsgType.TEXT
            responseContent = "感谢关注"
        }
    }
}
