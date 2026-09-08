package im.hikaru.ruoyi.module.mp.service.message

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template.MpMessageTemplateListReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.message.vo.template.MpMessageTemplateSendReqVO
import im.hikaru.ruoyi.module.mp.dal.dataobject.message.MpMessageTemplateDO
import im.hikaru.ruoyi.module.mp.dal.mysql.message.MpMessageTemplateDao
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MESSAGE_TEMPLATE_DELETE_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MESSAGE_TEMPLATE_NOT_EXISTS
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MESSAGE_TEMPLATE_SEND_FAIL
import im.hikaru.ruoyi.module.mp.enums.ErrorCodeConstants.MESSAGE_TEMPLATE_SYNC_FAIL
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.service.account.MpAccountService
import im.hikaru.ruoyi.module.mp.service.user.MpUserService
import me.chanjar.weixin.common.error.WxErrorException
import me.chanjar.weixin.mp.bean.template.WxMpTemplateData
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated

@Service
@Validated
class MpMessageTemplateServiceImpl(
    private val mpServiceFactoryProvider: ObjectProvider<MpServiceFactory>,
    private val mpAccountServiceProvider: ObjectProvider<MpAccountService>,
    private val mpUserService: MpUserService,
) : MpMessageTemplateService {
    private val mpServiceFactory: MpServiceFactory
        get() = mpServiceFactoryProvider.getObject()
    private val mpAccountService: MpAccountService
        get() = mpAccountServiceProvider.getObject()
    override fun deleteMessageTemplate(id: Long) {
        val template = validateMessageTemplateExists(id)
        try {
            mpServiceFactory.getRequiredMpService(requireNotNull(template.appId)).templateMsgService
                .delPrivateTemplate(requireNotNull(template.templateId))
        } catch (ex: Exception) {
            throw exception(MESSAGE_TEMPLATE_DELETE_FAIL, wxErrorMessage(ex))
        }
        MpMessageTemplateDao.deleteById(id)
    }

    override fun getMessageTemplate(id: Long): MpMessageTemplateDO = validateMessageTemplateExists(id)

    override fun getMessageTemplateList(listReqVO: MpMessageTemplateListReqVO): List<MpMessageTemplateDO> =
        MpMessageTemplateDao.selectList(listReqVO)

    @Transactional(rollbackFor = [Exception::class])
    override fun syncMessageTemplate(accountId: Long) {
        val account = mpAccountService.getRequiredAccount(accountId)
        val wxTemplates = try {
            mpServiceFactory.getRequiredMpService(accountId).templateMsgService.allPrivateTemplate
        } catch (ex: Exception) {
            throw exception(MESSAGE_TEMPLATE_SYNC_FAIL, wxErrorMessage(ex))
        }
        val existing = MpMessageTemplateDao.selectListByAppId(requireNotNull(account.appId))
            .associateBy { it.templateId }.toMutableMap()
        wxTemplates.orEmpty().forEach { wxTemplate ->
            val template = existing.remove(wxTemplate.templateId)
            if (template == null) {
                MpMessageTemplateDao.insert(MpMessageTemplateDO().apply {
                    this.accountId = account.id
                    appId = account.appId
                    templateId = wxTemplate.templateId
                    title = wxTemplate.title
                    content = wxTemplate.content
                    example = wxTemplate.example
                    primaryIndustry = wxTemplate.primaryIndustry
                    deputyIndustry = wxTemplate.deputyIndustry
                })
            } else {
                MpMessageTemplateDao.updateById(MpMessageTemplateDO().apply {
                    id = template.id
                    title = wxTemplate.title
                    content = wxTemplate.content
                    example = wxTemplate.example
                    primaryIndustry = wxTemplate.primaryIndustry
                    deputyIndustry = wxTemplate.deputyIndustry
                })
            }
        }
        MpMessageTemplateDao.deleteByIds(existing.values.mapNotNull { it.id })
    }

    override fun sendMessageTempalte(sendReqVO: MpMessageTemplateSendReqVO) {
        val user = mpUserService.getRequiredUser(requireNotNull(sendReqVO.userId))
        val template = validateMessageTemplateExists(requireNotNull(sendReqVO.id))
        val data = sendReqVO.data.orEmpty().map { (key, value) -> WxMpTemplateData(key, value) }
        val builder = WxMpTemplateMessage.builder()
            .templateId(template.templateId)
            .data(data)
            .toUser(user.openid)
        sendReqVO.url?.takeIf(String::isNotBlank)?.let(builder::url)
        sendReqVO.miniprogram?.takeIf(String::isNotBlank)?.let {
            builder.miniProgram(JsonUtils.parseObject(it, WxMpTemplateMessage.MiniProgram::class.java))
        }
        try {
            mpServiceFactory.getRequiredMpService(requireNotNull(template.appId)).templateMsgService
                .sendTemplateMsg(builder.build())
        } catch (ex: Exception) {
            throw exception(MESSAGE_TEMPLATE_SEND_FAIL, wxErrorMessage(ex))
        }
    }

    private fun validateMessageTemplateExists(id: Long): MpMessageTemplateDO =
        MpMessageTemplateDao.selectById(id) ?: throw exception(MESSAGE_TEMPLATE_NOT_EXISTS)

    private fun wxErrorMessage(ex: Exception): String =
        (ex as? WxErrorException)?.error?.errorMsg ?: ex.message.orEmpty()
}
