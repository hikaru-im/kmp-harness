package im.hikaru.ruoyi.module.system.service.social

import cn.binarywang.wx.miniapp.api.WxMaOrderShippingService
import cn.binarywang.wx.miniapp.api.WxMaQrcodeService
import cn.binarywang.wx.miniapp.api.WxMaService
import cn.binarywang.wx.miniapp.api.WxMaSubscribeService
import cn.binarywang.wx.miniapp.api.WxMaUserService
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.WxMaOrderShippingInfoNotifyConfirmRequest
import cn.binarywang.wx.miniapp.bean.shop.request.shipping.WxMaOrderShippingInfoUploadRequest
import cn.binarywang.wx.miniapp.bean.shop.response.WxMaOrderShippingInfoBaseResponse
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.api.social.SocialClientApiImpl
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxQrcodeReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderNotifyConfirmReceiveReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaOrderUploadShippingInfoReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialWxaSubscribeMessageSendReqDTO
import im.hikaru.ruoyi.module.system.dal.mysql.social.SocialClientTable
import im.hikaru.ruoyi.module.system.dal.mysql.social.SocialUserBindTable
import im.hikaru.ruoyi.module.system.dal.mysql.social.SocialUserTable
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import im.hikaru.ruoyi.module.system.framework.justauth.core.AuthRequestFactory
import me.chanjar.weixin.common.bean.WxJsapiSignature
import me.chanjar.weixin.common.bean.subscribemsg.TemplateInfo
import me.chanjar.weixin.mp.api.WxMpService
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.support.StaticListableBeanFactory
import java.time.LocalDateTime
import java.time.ZoneId

class SocialClientWeChatTest {

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:system_wechat_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(SocialClientTable, SocialUserTable, SocialUserBindTable) }
        TenantContextHolder.setTenantId(42L)
    }

    @AfterEach
    fun tearDown() = TenantContextHolder.clear()

    @Test
    fun `api maps wx responses and sends matching subscription template`() {
        val clientService = mock(SocialClientService::class.java)
        val userService = mock(SocialUserService::class.java)
        val api = SocialClientApiImpl(clientService, userService)
        val signature = WxJsapiSignature("app", "nonce", 123L, "https://example.test", "signature")
        val phone = WxMaPhoneNumberInfo().apply {
            phoneNumber = "+8613812345678"
            purePhoneNumber = "13812345678"
            countryCode = "86"
        }
        val template = TemplateInfo().apply {
            priTmplId = "template-id"
            title = "Order shipped"
            content = "thing1"
            example = "Parcel"
            type = 2
        }
        `when`(clientService.createWxMpJsapiSignature(2, signature.url)).thenReturn(signature)
        `when`(clientService.getWxMaPhoneNumberInfo(2, "phone-code")).thenReturn(phone)
        `when`(clientService.getSubscribeTemplateList(2)).thenReturn(listOf(template))
        `when`(userService.getSocialUserByUserId(2, 7L, SocialTypeEnum.WECHAT_MINI_PROGRAM.type))
            .thenReturn(SocialUserRespDTO(openid = "openid"))

        val mappedSignature = api.createWxMpJsapiSignature(2, signature.url)
        assertEquals("app", mappedSignature.appId)
        assertEquals("signature", mappedSignature.signature)
        assertEquals("13812345678", api.getWxMaPhoneNumberInfo(2, "phone-code").purePhoneNumber)
        assertEquals("template-id", api.getWxaSubscribeTemplateList(2).single().id)

        val request = SocialWxaSubscribeMessageSendReqDTO().apply {
            userId = 7L
            userType = 2
            templateTitle = "Order shipped"
        }
        api.sendWxaSubscribeMessage(request)
        verify(clientService).sendSubscribeMessage(request, "template-id", "openid")

        request.templateTitle = "Missing"
        api.sendWxaSubscribeMessage(request)
        verify(clientService, never()).sendSubscribeMessage(request, "missing", "openid")
    }

    @Test
    fun `service builds wx requests without network access`() {
        val wxMaService = mock(WxMaService::class.java)
        val userService = mock(WxMaUserService::class.java)
        val qrcodeService = mock(WxMaQrcodeService::class.java)
        val subscribeService = mock(WxMaSubscribeService::class.java)
        val shippingService = mock(WxMaOrderShippingService::class.java)
        `when`(wxMaService.userService).thenReturn(userService)
        `when`(wxMaService.qrcodeService).thenReturn(qrcodeService)
        `when`(wxMaService.subscribeService).thenReturn(subscribeService)
        `when`(wxMaService.wxMaOrderShippingService).thenReturn(shippingService)

        val beanFactory = StaticListableBeanFactory().apply { addBean("wxMaService", wxMaService) }
        val service = SocialClientServiceImpl(
            beanFactory.getBeanProvider(AuthRequestFactory::class.java),
            beanFactory.getBeanProvider(WxMpService::class.java),
            beanFactory.getBeanProvider(WxMaService::class.java),
        )

        val phone = WxMaPhoneNumberInfo().apply { purePhoneNumber = "13812345678" }
        `when`(userService.getPhoneNumber("phone-code")).thenReturn(phone)
        assertEquals("13812345678", service.getWxMaPhoneNumberInfo(2, "phone-code").purePhoneNumber)

        val code = byteArrayOf(1, 2, 3)
        `when`(qrcodeService.createWxaCodeUnlimitBytes("", "pages/order", true, "release", 430, true, null, true))
            .thenReturn(code)
        assertArrayEquals(code, service.getWxaQrcode(SocialWxQrcodeReqDTO().apply { path = "pages/order" }))

        val subscribeRequest = SocialWxaSubscribeMessageSendReqDTO().apply {
            userType = 2
            page = "pages/order"
            addMessage("thing1", "Parcel")
        }
        service.sendSubscribeMessage(subscribeRequest, "template-id", "openid")
        val messageCaptor = ArgumentCaptor.forClass(WxMaSubscribeMessage::class.java)
        verify(subscribeService).sendSubscribeMsg(messageCaptor.capture())
        assertEquals("template-id", messageCaptor.value.templateId)
        assertEquals("openid", messageCaptor.value.toUser)
        assertEquals("Parcel", messageCaptor.value.data.single().value)

        val shippingResponse = WxMaOrderShippingInfoBaseResponse().apply { errCode = 0 }
        `when`(shippingService.upload(any(WxMaOrderShippingInfoUploadRequest::class.java))).thenReturn(shippingResponse)
        service.uploadWxaOrderShippingInfo(2, SocialWxaOrderUploadShippingInfoReqDTO().apply {
            openid = "openid"
            transactionId = "transaction"
            logisticsType = SocialWxaOrderUploadShippingInfoReqDTO.LOGISTICS_TYPE_EXPRESS
            logisticsNo = "tracking"
            expressCompany = "express"
            itemDesc = "Parcel"
            receiverContact = "13812345678"
        })
        val shippingCaptor = ArgumentCaptor.forClass(WxMaOrderShippingInfoUploadRequest::class.java)
        verify(shippingService).upload(shippingCaptor.capture())
        assertEquals("138****5678", shippingCaptor.value.shippingList.single().contact.receiverContact)
        assertEquals("transaction", shippingCaptor.value.orderKey.transactionId)

        val notifyResponse = WxMaOrderShippingInfoBaseResponse().apply { errCode = 0 }
        `when`(shippingService.notifyConfirmReceive(any(WxMaOrderShippingInfoNotifyConfirmRequest::class.java)))
            .thenReturn(notifyResponse)
        val receivedTime = LocalDateTime.of(2026, 7, 18, 12, 0)
        service.notifyWxaOrderConfirmReceive(2, SocialWxaOrderNotifyConfirmReceiveReqDTO().apply {
            transactionId = "transaction"
            this.receivedTime = receivedTime
        })
        val notifyCaptor = ArgumentCaptor.forClass(WxMaOrderShippingInfoNotifyConfirmRequest::class.java)
        verify(shippingService).notifyConfirmReceive(notifyCaptor.capture())
        assertEquals(receivedTime.atZone(ZoneId.of("GMT+8")).toEpochSecond(), notifyCaptor.value.receivedTime)
    }
}
