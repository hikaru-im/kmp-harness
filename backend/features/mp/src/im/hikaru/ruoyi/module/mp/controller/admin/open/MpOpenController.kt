package im.hikaru.ruoyi.module.mp.controller.admin.open

import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.mp.controller.admin.open.vo.MpOpenCheckSignatureReqVO
import im.hikaru.ruoyi.module.mp.controller.admin.open.vo.MpOpenHandleMessageReqVO
import im.hikaru.ruoyi.module.mp.framework.mp.core.MpServiceFactory
import im.hikaru.ruoyi.module.mp.framework.mp.core.context.MpContextHolder
import im.hikaru.ruoyi.module.mp.service.account.MpAccountService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "管理后台 - 公众号回调")
@RestController
@RequestMapping("/mp/open")
@Validated
class MpOpenController(
    private val mpServiceFactory: MpServiceFactory,
    private val mpAccountService: MpAccountService,
) {
    @Operation(summary = "处理消息")
    @PostMapping("/{appId}", produces = ["application/xml; charset=UTF-8"])
    @TenantIgnore
    fun handleMessage(@PathVariable("appId") appId: String, @RequestBody content: String, reqVO: MpOpenHandleMessageReqVO): String {
        val account = requireNotNull(mpAccountService.getAccountFromCache(appId)) { "Mp account($appId) does not exist" }
        val oldTenantId = TenantContextHolder.getTenantId()
        val oldIgnore = TenantContextHolder.isIgnore()
        return try {
            MpContextHolder.setAppId(appId)
            TenantContextHolder.setTenantId(account.tenantId)
            TenantContextHolder.setIgnore(false)
            handleMessage0(appId, content, reqVO)
        } finally {
            MpContextHolder.clear()
            TenantContextHolder.setTenantId(oldTenantId)
            TenantContextHolder.setIgnore(oldIgnore)
        }
    }

    @Operation(summary = "校验签名")
    @GetMapping("/{appId}", produces = ["text/plain;charset=utf-8"])
    @TenantIgnore
    fun checkSignature(@PathVariable("appId") appId: String, reqVO: MpOpenCheckSignatureReqVO): String {
        val valid = mpServiceFactory.getRequiredMpService(appId).checkSignature(
            requireNotNull(reqVO.timestamp),
            requireNotNull(reqVO.nonce),
            requireNotNull(reqVO.signature),
        )
        return if (valid) reqVO.echostr.orEmpty() else "非法请求"
    }

    private fun handleMessage0(appId: String, content: String, reqVO: MpOpenHandleMessageReqVO): String {
        val service = mpServiceFactory.getRequiredMpService(appId)
        require(
            service.checkSignature(
                requireNotNull(reqVO.timestamp),
                requireNotNull(reqVO.nonce),
                requireNotNull(reqVO.signature),
            ),
        ) { "非法请求" }
        val inMessage = when (reqVO.encrypt_type) {
            null, "" -> me.chanjar.weixin.mp.bean.message.WxMpXmlMessage.fromXml(content)
            MpOpenHandleMessageReqVO.ENCRYPT_TYPE_AES -> me.chanjar.weixin.mp.bean.message.WxMpXmlMessage.fromEncryptedXml(
                content,
                service.wxMpConfigStorage,
                reqVO.timestamp,
                reqVO.nonce,
                reqVO.msg_signature,
            )
            else -> null
        } ?: error("消息解析失败，原因：消息为空")
        val outMessage = mpServiceFactory.getRequiredMpMessageRouter(appId).route(inMessage) ?: return ""
        return when (reqVO.encrypt_type) {
            null, "" -> outMessage.toXml()
            MpOpenHandleMessageReqVO.ENCRYPT_TYPE_AES -> outMessage.toEncryptedXml(service.wxMpConfigStorage)
            else -> ""
        }
    }
}
