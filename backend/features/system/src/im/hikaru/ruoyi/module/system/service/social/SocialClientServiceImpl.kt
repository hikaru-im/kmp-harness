package im.hikaru.ruoyi.module.system.service.social

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.beans.BeanUtils
import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientSaveReqVO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxQrcodeReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderNotifyConfirmReceiveReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderUploadShippingInfoReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaSubscribeMessageSendReqDTO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialClientDO
import im.hikaru.ruoyi.module.system.dal.mysql.social.SocialClientDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_UNIQUE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_WEIXIN_MINI_APP_ORDER_NOTIFY_CONFIRM_RECEIVE_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_WEIXIN_MINI_APP_ORDER_UPLOAD_SHIPPING_INFO_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_WEIXIN_MINI_APP_PHONE_CODE_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_WEIXIN_MINI_APP_QRCODE_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_WEIXIN_MINI_APP_SUBSCRIBE_MESSAGE_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_CLIENT_WEIXIN_MINI_APP_SUBSCRIBE_TEMPLATE_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.SOCIAL_USER_AUTH_FAILURE
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import im.hikaru.ruoyi.module.system.framework.justauth.core.AuthRequestFactory
import cn.binarywang.wx.miniapp.api.WxMaService
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.ContactBean
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.OrderKeyBean
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.PayerBean
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.ShippingListBean
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.WxMaOrderShippingInfoNotifyConfirmRequest
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.WxMaOrderShippingInfoUploadRequest
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import me.zhyd.oauth.config.AuthConfig
import me.zhyd.oauth.model.AuthCallback
import me.zhyd.oauth.model.AuthUser
import me.zhyd.oauth.request.AuthAlipayRequest
import me.zhyd.oauth.request.AuthRequest
import me.zhyd.oauth.utils.AuthStateUtils
import me.chanjar.weixin.common.bean.WxJsapiSignature
import me.chanjar.weixin.common.bean.subscribemsg.TemplateInfo
import me.chanjar.weixin.common.error.WxErrorException
import me.chanjar.weixin.mp.api.WxMpService
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.lang.reflect.Field
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Service
class SocialClientServiceImpl(
    private val authRequestFactoryProvider: ObjectProvider<AuthRequestFactory>,
    private val wxMpServiceProvider: ObjectProvider<WxMpService>,
    private val wxMaServiceProvider: ObjectProvider<WxMaService>,
    @param:Value("\${yudao.wxa-code.env-version:release}")
    private val envVersion: String = "release",
    @param:Value("\${yudao.wxa-subscribe-message.miniprogram-state:formal}")
    private val miniprogramState: String = "formal",
    @param:Value("\${wx.mp.appid:}")
    private val defaultWxMpAppId: String = "",
    @param:Value("\${wx.mp.secret:}")
    private val defaultWxMpSecret: String = "",
    @param:Value("\${wx.miniapp.appid:}")
    private val defaultWxMaAppId: String = "",
    @param:Value("\${wx.miniapp.secret:}")
    private val defaultWxMaSecret: String = "",
) : SocialClientService {

    private val wxMpServiceCache: LoadingCache<WxCredentials, WxMpService> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(10))
        .build(::buildWxMpService)

    private val wxMaServiceCache: LoadingCache<WxCredentials, WxMaService> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(10))
        .build(::buildWxMaService)

    override fun getAuthorizeUrl(socialType: Int, userType: Int, redirectUri: String): String {
        val authorizeUrl = buildAuthRequest(socialType, userType).authorize(AuthStateUtils.createState())
        return HttpUtils.replaceUrlQuery(authorizeUrl, "redirect_uri", redirectUri)
    }

    override fun getAuthUser(socialType: Int, userType: Int, code: String, state: String): AuthUser {
        val callback = AuthCallback.builder().code(code).auth_code(code).state(state).build()
        val response = buildAuthRequest(socialType, userType).login(callback)
        log.info("Social authorization response: type={}, ok={}, message={}", socialType, response.ok(), response.msg)
        if (!response.ok()) {
            throw ServiceExceptionUtil.exception(SOCIAL_USER_AUTH_FAILURE, response.msg)
        }
        return response.data
            ?: throw ServiceExceptionUtil.exception(SOCIAL_USER_AUTH_FAILURE, "provider returned no user")
    }

    override fun createWxMpJsapiSignature(userType: Int, url: String): WxJsapiSignature =
        getWxMpService(userType).createJsapiSignature(url)

    override fun getWxMaPhoneNumberInfo(userType: Int, phoneCode: String): WxMaPhoneNumberInfo = try {
        getWxMaService(userType).userService.getPhoneNumber(phoneCode)
    } catch (ex: WxErrorException) {
        log.error("Failed to obtain WeChat Mini Program phone number: userType={}", userType, ex)
        throw ServiceExceptionUtil.exception(SOCIAL_CLIENT_WEIXIN_MINI_APP_PHONE_CODE_ERROR)
    }

    override fun getWxaQrcode(reqVO: SocialWxQrcodeReqDTO): ByteArray = try {
        getWxMaService(UserTypeEnum.MEMBER.value).qrcodeService.createWxaCodeUnlimitBytes(
            reqVO.scene?.takeIf { it.isNotEmpty() } ?: SocialWxQrcodeReqDTO.SCENE,
            requireNotNull(reqVO.path),
            reqVO.checkPath ?: SocialWxQrcodeReqDTO.CHECK_PATH,
            envVersion,
            reqVO.width ?: SocialWxQrcodeReqDTO.WIDTH,
            reqVO.autoColor ?: SocialWxQrcodeReqDTO.AUTO_COLOR,
            null,
            reqVO.hyaline ?: SocialWxQrcodeReqDTO.HYALINE,
        )
    } catch (ex: WxErrorException) {
        log.error("Failed to obtain WeChat Mini Program code: request={}", reqVO, ex)
        throw ServiceExceptionUtil.exception(SOCIAL_CLIENT_WEIXIN_MINI_APP_QRCODE_ERROR)
    }

    @Cacheable(cacheNames = [RedisKeyConstants.WXA_SUBSCRIBE_TEMPLATE], key = "#userType", unless = "#result == null")
    override fun getSubscribeTemplateList(userType: Int): List<TemplateInfo> = try {
        getWxMaService(userType).subscribeService.templateList
    } catch (ex: WxErrorException) {
        log.error("Failed to obtain WeChat subscription templates: userType={}", userType, ex)
        throw ServiceExceptionUtil.exception(SOCIAL_CLIENT_WEIXIN_MINI_APP_SUBSCRIBE_TEMPLATE_ERROR)
    }

    override fun sendSubscribeMessage(
        reqDTO: SocialWxaSubscribeMessageSendReqDTO,
        templateId: String,
        openId: String,
    ) {
        val message = WxMaSubscribeMessage()
            .setLang("zh_CN")
            .setMiniprogramState(miniprogramState)
            .setTemplateId(templateId)
            .setToUser(openId)
            .setPage(reqDTO.page)
        reqDTO.messages.orEmpty().forEach { (key, value) ->
            message.addData(WxMaSubscribeMessage.MsgData(key, value))
        }
        try {
            getWxMaService(requireNotNull(reqDTO.userType)).subscribeService.sendSubscribeMsg(message)
        } catch (ex: WxErrorException) {
            log.error("Failed to send WeChat subscription message: templateId={}, openId={}", templateId, openId, ex)
            throw ServiceExceptionUtil.exception(SOCIAL_CLIENT_WEIXIN_MINI_APP_SUBSCRIBE_MESSAGE_ERROR)
        }
    }

    override fun uploadWxaOrderShippingInfo(userType: Int, reqDTO: SocialWxaOrderUploadShippingInfoReqDTO) {
        val shipping = ShippingListBean().apply {
            itemDesc = requireNotNull(reqDTO.itemDesc)
            if (reqDTO.logisticsType == SocialWxaOrderUploadShippingInfoReqDTO.LOGISTICS_TYPE_EXPRESS) {
                trackingNo = reqDTO.logisticsNo
                expressCompany = reqDTO.expressCompany
                contact = ContactBean().apply {
                    receiverContact = desensitizeMobile(requireNotNull(reqDTO.receiverContact))
                }
            }
        }
        val request = WxMaOrderShippingInfoUploadRequest().apply {
            orderKey = OrderKeyBean().apply {
                orderNumberType = 2
                transactionId = requireNotNull(reqDTO.transactionId)
            }
            logisticsType = requireNotNull(reqDTO.logisticsType)
            deliveryMode = 1
            shippingList = listOf(shipping)
            payer = PayerBean().apply { openid = requireNotNull(reqDTO.openid) }
            uploadTime = ZonedDateTime.now().format(WECHAT_TIME_FORMATTER)
        }
        val service = getWxMaService(userType).wxMaOrderShippingService
        for (attempt in 0..UPLOAD_SHIPPING_INFO_RETRY_BACKOFF_MILLIS.size) {
            try {
                service.upload(request)
                log.info("Uploaded WeChat Mini Program shipping information: request={}", request)
                return
            } catch (ex: WxErrorException) {
                if (ex.error?.errorCode == WX_ERR_CODE_PAY_ORDER_NOT_EXIST &&
                    attempt < UPLOAD_SHIPPING_INFO_RETRY_BACKOFF_MILLIS.size
                ) {
                    val delayMillis = UPLOAD_SHIPPING_INFO_RETRY_BACKOFF_MILLIS[attempt]
                    log.warn("WeChat payment order is not ready; retrying in {} ms: request={}", delayMillis, request)
                    sleep(delayMillis)
                    continue
                }
                val message = ex.error?.errorMsg ?: ex.message.orEmpty()
                log.error("Failed to upload WeChat Mini Program shipping information: request={}", request, ex)
                throw ServiceExceptionUtil.exception(
                    SOCIAL_CLIENT_WEIXIN_MINI_APP_ORDER_UPLOAD_SHIPPING_INFO_ERROR,
                    message,
                )
            }
        }
    }

    override fun notifyWxaOrderConfirmReceive(userType: Int, reqDTO: SocialWxaOrderNotifyConfirmReceiveReqDTO) {
        val request = WxMaOrderShippingInfoNotifyConfirmRequest().apply {
            transactionId = requireNotNull(reqDTO.transactionId)
            receivedTime = requireNotNull(reqDTO.receivedTime).atZone(DEFAULT_ZONE_ID).toEpochSecond()
        }
        try {
            val response = getWxMaService(userType).wxMaOrderShippingService.notifyConfirmReceive(request)
            if ((response.errCode ?: 0) != 0) {
                throw ServiceExceptionUtil.exception(
                    SOCIAL_CLIENT_WEIXIN_MINI_APP_ORDER_NOTIFY_CONFIRM_RECEIVE_ERROR,
                    response.errMsg.orEmpty(),
                )
            }
            log.info("Notified WeChat Mini Program order receipt: request={}", request)
        } catch (ex: WxErrorException) {
            val message = ex.error?.errorMsg ?: ex.message.orEmpty()
            log.error("Failed to notify WeChat Mini Program order receipt: request={}", request, ex)
            throw ServiceExceptionUtil.exception(
                SOCIAL_CLIENT_WEIXIN_MINI_APP_ORDER_NOTIFY_CONFIRM_RECEIVE_ERROR,
                message,
            )
        }
    }

    override fun createSocialClient(req: SocialClientSaveReqVO): Long {
        val userType = requireNotNull(req.userType)
        val socialType = requireNotNull(req.socialType)
        validateSocialClientUnique(null, userType, socialType)
        return SocialClientDao.insert(req.toEntity())
    }

    override fun updateSocialClient(req: SocialClientSaveReqVO) {
        val id = requireNotNull(req.id)
        validateSocialClientExists(id)
        validateSocialClientUnique(id, requireNotNull(req.userType), requireNotNull(req.socialType))
        SocialClientDao.updateById(req.toEntity())
    }

    override fun deleteSocialClient(id: Long) {
        validateSocialClientExists(id)
        SocialClientDao.deleteById(id)
    }

    override fun deleteSocialClientList(ids: Collection<Long>) {
        SocialClientDao.deleteByIds(ids)
    }

    override fun getSocialClient(id: Long): SocialClientDO? = SocialClientDao.selectById(id)

    override fun getSocialClientPage(req: SocialClientPageReqVO): PageResult<SocialClientDO> = SocialClientDao.selectPage(req)

    internal fun buildAuthRequest(socialType: Int, userType: Int): AuthRequest {
        val source = SocialTypeEnum.valueOfType(socialType)
            ?: throw ServiceExceptionUtil.exception(SOCIAL_USER_AUTH_FAILURE, "unsupported social type $socialType")
        val factory = authRequestFactoryProvider.ifAvailable
            ?: throw ServiceExceptionUtil.exception(SOCIAL_USER_AUTH_FAILURE, "JustAuth is not configured")
        val request = runCatching { factory.get(source.source) }.getOrElse {
            throw ServiceExceptionUtil.exception(SOCIAL_USER_AUTH_FAILURE, it.message ?: "provider is not configured")
        }
        val client = SocialClientDao.selectBySocialTypeAndUserType(socialType, userType)
        if (client == null || !CommonStatusEnum.isEnable(client.status)) return request
        return overrideClientConfig(request, client, source)
    }

    private fun overrideClientConfig(
        request: AuthRequest,
        client: SocialClientDO,
        source: SocialTypeEnum,
    ): AuthRequest {
        val configField = findField(request.javaClass, "config")
        val original = configField.get(request) as AuthConfig
        val overridden = AuthConfig()
        BeanUtils.copyProperties(original, overridden)
        overridden.clientId = requireNotNull(client.clientId)
        overridden.clientSecret = requireNotNull(client.clientSecret)
        client.agentId?.let { overridden.agentId = it }
        if (source == SocialTypeEnum.ALIPAY_MINI_PROGRAM) {
            return AuthAlipayRequest(overridden, client.publicKey)
        }
        configField.set(request, overridden)
        return request
    }

    internal fun getWxMpService(userType: Int): WxMpService {
        val client = SocialClientDao.selectBySocialTypeAndUserType(SocialTypeEnum.WECHAT_MP.type, userType)
        if (client != null && CommonStatusEnum.isEnable(client.status)) {
            return wxMpServiceCache.get(client.toWxCredentials())
        }
        wxMpServiceProvider.ifAvailable?.let { return it }
        if (defaultWxMpAppId.isNotBlank() && defaultWxMpSecret.isNotBlank()) {
            return wxMpServiceCache.get(WxCredentials(defaultWxMpAppId, defaultWxMpSecret))
        }
        throw ServiceExceptionUtil.exception(SOCIAL_USER_AUTH_FAILURE, "WeChat MP client is not configured")
    }

    internal fun getWxMaService(userType: Int): WxMaService {
        val client = SocialClientDao.selectBySocialTypeAndUserType(SocialTypeEnum.WECHAT_MINI_PROGRAM.type, userType)
        if (client != null && CommonStatusEnum.isEnable(client.status)) {
            return wxMaServiceCache.get(client.toWxCredentials())
        }
        wxMaServiceProvider.ifAvailable?.let { return it }
        if (defaultWxMaAppId.isNotBlank() && defaultWxMaSecret.isNotBlank()) {
            return wxMaServiceCache.get(WxCredentials(defaultWxMaAppId, defaultWxMaSecret))
        }
        throw ServiceExceptionUtil.exception(SOCIAL_USER_AUTH_FAILURE, "WeChat Mini Program client is not configured")
    }

    private fun buildWxMpService(credentials: WxCredentials): WxMpService {
        val config = WxMpDefaultConfigImpl().apply {
            appId = credentials.clientId
            secret = credentials.clientSecret
        }
        return WxMpServiceImpl().apply { wxMpConfigStorage = config }
    }

    private fun buildWxMaService(credentials: WxCredentials): WxMaService {
        val config = WxMaDefaultConfigImpl().apply {
            appid = credentials.clientId
            secret = credentials.clientSecret
        }
        return WxMaServiceImpl().apply { wxMaConfig = config }
    }

    private fun SocialClientDO.toWxCredentials(): WxCredentials = WxCredentials(
        requireNotNull(clientId) { "WeChat client id must not be null" },
        requireNotNull(clientSecret) { "WeChat client secret must not be null" },
    )

    private fun validateSocialClientExists(id: Long) {
        if (SocialClientDao.selectById(id) == null) {
            throw ServiceExceptionUtil.exception(SOCIAL_CLIENT_NOT_EXISTS)
        }
    }

    private fun validateSocialClientUnique(id: Long?, userType: Int, socialType: Int) {
        val existing = SocialClientDao.selectBySocialTypeAndUserType(socialType, userType) ?: return
        if (id == null || existing.id != id) {
            throw ServiceExceptionUtil.exception(SOCIAL_CLIENT_UNIQUE)
        }
    }

    private fun SocialClientSaveReqVO.toEntity() = SocialClientDO().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        socialType = this@toEntity.socialType
        userType = this@toEntity.userType
        clientId = this@toEntity.clientId
        clientSecret = this@toEntity.clientSecret
        agentId = this@toEntity.agentId
        publicKey = this@toEntity.publicKey
        status = this@toEntity.status
    }

    private fun findField(type: Class<*>, name: String): Field {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current.getDeclaredField(name) }.getOrNull()?.let {
                it.isAccessible = true
                return it
            }
            current = current.superclass
        }
        throw IllegalStateException("Field $name was not found on ${type.name}")
    }

    private companion object {
        val log = LoggerFactory.getLogger(SocialClientServiceImpl::class.java)
        val DEFAULT_ZONE_ID: ZoneId = ZoneId.of("GMT+8")
        val WECHAT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        val UPLOAD_SHIPPING_INFO_RETRY_BACKOFF_MILLIS = longArrayOf(1_000L, 2_000L, 4_000L)
        const val WX_ERR_CODE_PAY_ORDER_NOT_EXIST = 10_060_001
    }

    private data class WxCredentials(
        val clientId: String,
        val clientSecret: String,
    )
}

private fun desensitizeMobile(value: String): String {
    if (value.length <= 7) return value
    return value.take(3) + "*".repeat(value.length - 7) + value.takeLast(4)
}

private fun sleep(delayMillis: Long) {
    try {
        Thread.sleep(delayMillis)
    } catch (ex: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IllegalStateException("Interrupted while retrying WeChat request", ex)
    }
}
