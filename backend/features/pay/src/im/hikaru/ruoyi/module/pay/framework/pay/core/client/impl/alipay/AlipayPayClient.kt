package im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.alipay

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.enums.refund.PayRefundStatusEnum
import im.hikaru.ruoyi.module.pay.enums.transfer.PayTransferStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClient
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.enums.PayOrderDisplayModeEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.exception.PayClientException
import com.alipay.api.AlipayResponse
import com.alipay.api.AlipayConfig
import com.alipay.api.DefaultAlipayClient
import com.alipay.api.domain.AlipayFundTransCommonQueryModel
import com.alipay.api.domain.AlipayFundTransUniTransferModel
import com.alipay.api.domain.AlipayTradeFastpayRefundQueryModel
import com.alipay.api.domain.AlipayTradePagePayModel
import com.alipay.api.domain.AlipayTradePayModel
import com.alipay.api.domain.AlipayTradePrecreateModel
import com.alipay.api.domain.AlipayTradeQueryModel
import com.alipay.api.domain.AlipayTradeRefundModel
import com.alipay.api.domain.AlipayTradeWapPayModel
import com.alipay.api.domain.AlipayTradeAppPayModel
import com.alipay.api.domain.Participant
import com.alipay.api.internal.util.AlipaySignature
import com.alipay.api.internal.util.AntCertificationUtil
import com.alipay.api.request.AlipayFundTransCommonQueryRequest
import com.alipay.api.request.AlipayFundTransUniTransferRequest
import com.alipay.api.request.AlipayTradeFastpayRefundQueryRequest
import com.alipay.api.request.AlipayTradePagePayRequest
import com.alipay.api.request.AlipayTradePayRequest
import com.alipay.api.request.AlipayTradePrecreateRequest
import com.alipay.api.request.AlipayTradeQueryRequest
import com.alipay.api.request.AlipayTradeRefundRequest
import com.alipay.api.request.AlipayTradeWapPayRequest
import com.alipay.api.request.AlipayTradeAppPayRequest
import com.alipay.api.response.AlipayFundTransCommonQueryResponse
import com.alipay.api.response.AlipayFundTransUniTransferResponse
import com.alipay.api.response.AlipayTradeFastpayRefundQueryResponse
import com.alipay.api.response.AlipayTradePagePayResponse
import com.alipay.api.response.AlipayTradePayResponse
import com.alipay.api.response.AlipayTradePrecreateResponse
import com.alipay.api.response.AlipayTradeQueryResponse
import com.alipay.api.response.AlipayTradeRefundResponse
import com.alipay.api.response.AlipayTradeWapPayResponse
import com.alipay.api.response.AlipayTradeAppPayResponse
import java.math.BigDecimal
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Date

/** SDK-backed Alipay adapter for all five Alipay channel variants. */
class AlipayPayClient(
    override val id: Long,
    private val channelCode: String,
    override var config: AlipayPayClientConfig,
) : PayClient<AlipayPayClientConfig> {
    @Volatile
    private var client: DefaultAlipayClient? = null

    override fun unifiedOrder(reqDTO: PayOrderUnifiedReqDTO): PayOrderRespDTO = try {
        when (channelCode) {
            PayChannelEnum.ALIPAY_PC.code -> unifiedPc(reqDTO)
            PayChannelEnum.ALIPAY_WAP.code -> unifiedWap(reqDTO)
            PayChannelEnum.ALIPAY_APP.code -> unifiedApp(reqDTO)
            PayChannelEnum.ALIPAY_QR.code -> unifiedQr(reqDTO)
            PayChannelEnum.ALIPAY_BAR.code -> unifiedBar(reqDTO)
            else -> throw IllegalArgumentException("Unsupported Alipay channel: $channelCode")
        }
    } catch (ex: PayClientException) {
        throw ex
    } catch (ex: Throwable) {
        throw PayClientException("Alipay order request failed", ex)
    }

    override fun parseOrderNotify(
        params: Map<String, String>,
        body: String,
        headers: Map<String, String>,
    ): PayOrderRespDTO {
        val values = LinkedHashMap<String, String>().apply {
            putAll(params)
            putAll(parseForm(body))
        }
        verifyNotify(values)
        val status = when {
            (values["refund_fee"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO) > BigDecimal.ZERO -> PayOrderStatusEnum.REFUND.status
            else -> parseOrderStatus(values["trade_status"])
        }
        return PayOrderRespDTO.of(
            status,
            values["trade_no"],
            values["seller_id"],
            parseTime(values["gmt_payment"]),
            values["out_trade_no"],
            values,
        )
    }

    override fun getOrder(outTradeNo: String): PayOrderRespDTO {
        val request = AlipayTradeQueryRequest().apply {
            bizModel = AlipayTradeQueryModel().apply { this.outTradeNo = outTradeNo }
        }
        val response: AlipayTradeQueryResponse = execute(request)
        if (!response.isSuccess) return PayOrderRespDTO.closedOf(response.subCode, response.subMsg, outTradeNo, response)
        val status = parseOrderStatus(response.tradeStatus)
        return PayOrderRespDTO.of(
            status,
            response.tradeNo,
            response.buyerUserId,
            response.sendPayDate?.let(::toLocalDateTime),
            outTradeNo,
            response,
        )
    }

    override fun unifiedRefund(reqDTO: PayRefundUnifiedReqDTO): PayRefundRespDTO {
        return try {
        val request = AlipayTradeRefundRequest().apply {
            bizModel = AlipayTradeRefundModel().apply {
                outTradeNo = reqDTO.outTradeNo
                outRequestNo = reqDTO.outRefundNo
                refundAmount = amount(reqDTO.refundPrice)
                refundReason = reqDTO.reason
            }
        }
        val response: AlipayTradeRefundResponse = execute(request)
        if (!response.isSuccess) {
            if (response.subCode == "ACQ.SYSTEM_ERROR" || response.subCode == "SYSTEM_ERROR") {
                PayRefundRespDTO.waitingOf(null, reqDTO.outRefundNo, response)
            } else if (response.subCode != null) {
                PayRefundRespDTO.failureOf(response.subCode, response.subMsg, reqDTO.outRefundNo, response)
            } else {
                PayRefundRespDTO.failureOf(null, response.subMsg, reqDTO.outRefundNo, response)
            }
        } else {
            PayRefundRespDTO.successOf(null, response.gmtRefundPay?.let(::toLocalDateTime), reqDTO.outRefundNo, response)
        }
        } catch (ex: Throwable) {
            throw PayClientException("Alipay refund request failed", ex)
        }
    }

    override fun parseRefundNotify(
        params: Map<String, String>,
        body: String,
        headers: Map<String, String>,
    ): PayRefundRespDTO = throw UnsupportedOperationException("Alipay has no refund callback")

    override fun getRefund(outTradeNo: String, outRefundNo: String): PayRefundRespDTO {
        val request = AlipayTradeFastpayRefundQueryRequest().apply {
            bizModel = AlipayTradeFastpayRefundQueryModel().apply {
                this.outTradeNo = outTradeNo
                outRequestNo = outRefundNo
                queryOptions = listOf("gmt_refund_pay")
            }
        }
        val response: AlipayTradeFastpayRefundQueryResponse = execute(request)
        if (!response.isSuccess) {
            if (response.subCode == "TRADE_NOT_EXIST" || response.subCode == "ACQ.TRADE_NOT_EXIST") {
                return PayRefundRespDTO.failureOf(response.subCode, response.subMsg, outRefundNo, response)
            }
            return PayRefundRespDTO.waitingOf(null, outRefundNo, response)
        }
        return if (response.refundStatus == "REFUND_SUCCESS") {
            PayRefundRespDTO.successOf(null, response.gmtRefundPay?.let(::toLocalDateTime), outRefundNo, response)
        } else {
            PayRefundRespDTO.waitingOf(null, outRefundNo, response)
        }
    }

    override fun unifiedTransfer(reqDTO: PayTransferUnifiedReqDTO): PayTransferRespDTO {
        return try {
        val model = AlipayFundTransUniTransferModel().apply {
            transAmount = amount(reqDTO.price)
            orderTitle = reqDTO.subject
            outBizNo = reqDTO.outTransferNo
            productCode = "TRANS_ACCOUNT_NO_PWD"
            bizScene = "DIRECT_TRANSFER"
            businessParams = reqDTO.channelExtras?.let(JsonUtils::toJsonString)
            transferSceneName = reqDTO.channelExtras?.get("sceneName")
            payeeInfo = Participant().apply {
                identityType = "ALIPAY_LOGON_ID"
                identity = reqDTO.userAccount
                name = reqDTO.userName
            }
        }
        val response: AlipayFundTransUniTransferResponse = execute(AlipayFundTransUniTransferRequest().apply { bizModel = model })
        if (!response.isSuccess) {
            if (response.subCode in setOf("PAYMENT_INFO_INCONSISTENCY", "SYSTEM_ERROR", "ACQ.SYSTEM_ERROR")) {
                PayTransferRespDTO.waitingOf(null, reqDTO.outTransferNo, response)
            } else {
                PayTransferRespDTO.closedOf(response.subCode, response.subMsg, reqDTO.outTransferNo, response)
            }
        } else {
            when (response.status) {
                "REFUND", "FAIL" -> PayTransferRespDTO.closedOf(response.subCode, response.subMsg, reqDTO.outTransferNo, response)
                "DEALING" -> PayTransferRespDTO.processingOf(response.orderId, reqDTO.outTransferNo, response)
                else -> PayTransferRespDTO.successOf(response.orderId, parseTime(response.transDate), response.outBizNo, response)
            }
        }
        } catch (ex: Throwable) {
            throw PayClientException("Alipay transfer request failed", ex)
        }
    }

    override fun getTransfer(outTradeNo: String): PayTransferRespDTO {
        val model = AlipayFundTransCommonQueryModel().apply {
            productCode = "TRANS_ACCOUNT_NO_PWD"
            bizScene = "DIRECT_TRANSFER"
            outBizNo = outTradeNo
        }
        val response: AlipayFundTransCommonQueryResponse = execute(AlipayFundTransCommonQueryRequest().apply { bizModel = model })
        if (!response.isSuccess) {
            if (response.subCode in setOf("ORDER_NOT_EXIST", "SYSTEM_ERROR", "ACQ.SYSTEM_ERROR")) {
                return PayTransferRespDTO.waitingOf(null, outTradeNo, response)
            }
            return PayTransferRespDTO.closedOf(response.subCode, response.subMsg, outTradeNo, response)
        }
        return when (response.status) {
            "REFUND", "FAIL" -> PayTransferRespDTO.closedOf(response.subCode, response.subMsg, outTradeNo, response)
            "DEALING" -> PayTransferRespDTO.processingOf(response.orderId, outTradeNo, response)
            else -> PayTransferRespDTO.successOf(response.orderId, parseTime(response.payDate), response.outBizNo, response)
        }
    }

    override fun parseTransferNotify(
        params: Map<String, String>,
        body: String,
        headers: Map<String, String>,
    ): PayTransferRespDTO {
        val values = LinkedHashMap<String, String>().apply {
            putAll(params)
            putAll(parseForm(body))
        }
        verifyNotify(values)
        val outBizNo = values["out_biz_no"]
        return when (values["status"]) {
            "SUCCESS" -> PayTransferRespDTO.successOf(values["order_id"], parseTime(values["pay_date"]), outBizNo, values)
            "DEALING" -> PayTransferRespDTO.processingOf(values["order_id"], outBizNo, values)
            "REFUND", "FAIL" -> PayTransferRespDTO.closedOf(values["sub_code"], values["sub_msg"], outBizNo, values)
            else -> PayTransferRespDTO.waitingOf(values["order_id"], outBizNo, values)
        }
    }

    fun refresh(newConfig: AlipayPayClientConfig) {
        config = newConfig
        client = null
    }

    private fun unifiedPc(req: PayOrderUnifiedReqDTO): PayOrderRespDTO {
        val displayMode = req.displayMode ?: PayOrderDisplayModeEnum.URL.mode
        val request = AlipayTradePagePayRequest().apply {
            bizModel = AlipayTradePagePayModel().apply {
                outTradeNo = req.outTradeNo
                subject = req.subject
                body = req.body
                totalAmount = amount(req.price)
                timeExpire = formatTime(req.expireTime)
                productCode = "FAST_INSTANT_TRADE_PAY"
                qrPayMode = "2"
            }
            notifyUrl = req.notifyUrl
            returnUrl = req.returnUrl
        }
        val response: AlipayTradePagePayResponse = if (displayMode == PayOrderDisplayModeEnum.FORM.mode) {
            client().pageExecute(request, "POST")
        } else {
            client().pageExecute(request, "GET")
        }
        return if (!response.isSuccess) closedOrder(req, response) else
            PayOrderRespDTO.waitingOf(displayMode, response.body, req.outTradeNo, response)
    }

    private fun unifiedWap(req: PayOrderUnifiedReqDTO): PayOrderRespDTO {
        val request = AlipayTradeWapPayRequest().apply {
            bizModel = AlipayTradeWapPayModel().apply {
                outTradeNo = req.outTradeNo
                subject = req.subject
                body = req.body
                totalAmount = amount(req.price)
                productCode = "QUICK_WAP_PAY"
                quitUrl = req.returnUrl
                timeExpire = formatTime(req.expireTime)
            }
            notifyUrl = req.notifyUrl
            returnUrl = req.returnUrl
        }
        val response: AlipayTradeWapPayResponse = client().pageExecute(request, "GET")
        return if (!response.isSuccess) closedOrder(req, response) else
            PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.URL.mode, response.body, req.outTradeNo, response)
    }

    private fun unifiedApp(req: PayOrderUnifiedReqDTO): PayOrderRespDTO {
        val request = AlipayTradeAppPayRequest().apply {
            bizModel = AlipayTradeAppPayModel().apply {
                outTradeNo = req.outTradeNo
                subject = req.subject
                body = req.body
                totalAmount = amount(req.price)
                timeExpire = formatTime(req.expireTime)
                productCode = "QUICK_MSECURITY_PAY"
            }
            notifyUrl = req.notifyUrl
            returnUrl = req.returnUrl
        }
        val response: AlipayTradeAppPayResponse = client().sdkExecute(request)
        return if (!response.isSuccess) closedOrder(req, response) else
            PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.APP.mode, response.body, req.outTradeNo, response)
    }

    private fun unifiedQr(req: PayOrderUnifiedReqDTO): PayOrderRespDTO {
        val request = AlipayTradePrecreateRequest().apply {
            bizModel = AlipayTradePrecreateModel().apply {
                outTradeNo = req.outTradeNo
                subject = req.subject
                body = req.body
                totalAmount = amount(req.price)
                productCode = "FACE_TO_FACE_PAYMENT"
            }
            notifyUrl = req.notifyUrl
            returnUrl = req.returnUrl
        }
        val response: AlipayTradePrecreateResponse = execute(request)
        return if (!response.isSuccess) closedOrder(req, response) else
            PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.QR_CODE.mode, response.qrCode, req.outTradeNo, response)
    }

    private fun unifiedBar(req: PayOrderUnifiedReqDTO): PayOrderRespDTO {
        val authCode = req.channelExtras?.get("auth_code")?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("auth_code is required for Alipay bar payment")
        val request = AlipayTradePayRequest().apply {
            bizModel = AlipayTradePayModel().apply {
                outTradeNo = req.outTradeNo
                subject = req.subject
                body = req.body
                totalAmount = amount(req.price)
                scene = "bar_code"
                this.authCode = authCode
            }
            notifyUrl = req.notifyUrl
            returnUrl = req.returnUrl
        }
        val response: AlipayTradePayResponse = execute(request)
        if (!response.isSuccess) return closedOrder(req, response)
        return if (response.code == "10000") {
            PayOrderRespDTO.successOf(
                response.tradeNo,
                response.buyerUserId,
                response.gmtPayment?.let(::toLocalDateTime),
                response.outTradeNo ?: req.outTradeNo,
                response,
            ).setDisplayMode(PayOrderDisplayModeEnum.BAR_CODE.mode).setDisplayContent("")
        } else {
            PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.BAR_CODE.mode, "", req.outTradeNo, response)
        }
    }

    private fun client(): DefaultAlipayClient {
        client?.let { return it }
        synchronized(this) {
            client?.let { return it }
            val sdkConfig = AlipayConfig().apply {
                serverUrl = requireNotNull(config.serverUrl)
                appId = requireNotNull(config.appId)
                format = "json"
                charset = StandardCharsets.UTF_8.name()
                signType = config.signType ?: AlipayPayClientConfig.SIGN_TYPE_DEFAULT
                privateKey = config.privateKey
                alipayPublicKey = config.alipayPublicKey
                appCertContent = config.appCertContent
                alipayPublicCertContent = config.alipayPublicCertContent
                rootCertContent = config.rootCertContent
                encryptType = config.encryptType
                encryptKey = config.encryptKey
            }
            return DefaultAlipayClient(sdkConfig).also { client = it }
        }
    }

    private fun <T : AlipayResponse> execute(request: com.alipay.api.AlipayRequest<T>): T =
        if (config.mode == AlipayPayClientConfig.MODE_CERTIFICATE) client().certificateExecute(request) else client().execute(request)

    private fun closedOrder(req: PayOrderUnifiedReqDTO, response: AlipayResponse) =
        PayOrderRespDTO.closedOf(response.subCode, response.subMsg, req.outTradeNo, response)

    private fun verifyNotify(values: Map<String, String>) {
        val verified = if (config.mode == AlipayPayClientConfig.MODE_CERTIFICATE) {
            val cert = AntCertificationUtil.getCertFromContent(requireNotNull(config.alipayPublicCertContent))
            val key = Base64.getEncoder().encodeToString(cert.publicKey.encoded)
            AlipaySignature.rsaCheckV1(values, key, StandardCharsets.UTF_8.name(), config.signType ?: AlipayPayClientConfig.SIGN_TYPE_DEFAULT)
        } else {
            AlipaySignature.rsaCheckV1(
                values,
                requireNotNull(config.alipayPublicKey),
                StandardCharsets.UTF_8.name(),
                config.signType ?: AlipayPayClientConfig.SIGN_TYPE_DEFAULT,
            )
        }
        check(verified) { "Alipay notify signature verification failed" }
    }

    private fun parseForm(body: String): Map<String, String> = body.split('&')
        .asSequence()
        .mapNotNull { part ->
            val index = part.indexOf('=')
            if (index < 0) null else URLDecoder.decode(part.substring(0, index), StandardCharsets.UTF_8) to
                URLDecoder.decode(part.substring(index + 1), StandardCharsets.UTF_8)
        }
        .toMap()

    private fun parseOrderStatus(value: String?): Int = when (value) {
        "WAIT_BUYER_PAY" -> PayOrderStatusEnum.WAITING.status
        "TRADE_FINISHED", "TRADE_SUCCESS" -> PayOrderStatusEnum.SUCCESS.status
        "TRADE_CLOSED" -> PayOrderStatusEnum.CLOSED.status
        else -> throw IllegalArgumentException("Unknown Alipay trade status: $value")
    }

    private fun amount(cents: Int?): String = BigDecimal.valueOf(requireNotNull(cents).toLong(), 2).toPlainString()

    private fun formatTime(value: java.time.LocalDateTime?): String? = value?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

    private fun parseTime(value: String?): LocalDateTime? = value?.takeIf(String::isNotBlank)?.let {
        runCatching { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }.getOrNull()
    }

    private fun toLocalDateTime(value: Date): LocalDateTime = value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
}
