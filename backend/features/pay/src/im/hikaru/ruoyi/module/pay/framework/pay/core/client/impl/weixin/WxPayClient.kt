package im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.weixin

import im.hikaru.ruoyi.framework.common.util.io.FileUtils
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.pay.enums.PayChannelEnum
import im.hikaru.ruoyi.module.pay.enums.order.PayOrderStatusEnum
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClient
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferRespDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.dto.transfer.PayTransferUnifiedReqDTO
import im.hikaru.ruoyi.module.pay.framework.pay.core.enums.PayOrderDisplayModeEnum
import com.github.binarywang.wxpay.bean.notify.SignatureHeader
import com.github.binarywang.wxpay.bean.notify.WxPayNotifyV3Result
import com.github.binarywang.wxpay.bean.notify.WxPayOrderNotifyResult
import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyResult
import com.github.binarywang.wxpay.bean.notify.WxPayRefundNotifyV3Result
import com.github.binarywang.wxpay.bean.request.WxPayMicropayRequest
import com.github.binarywang.wxpay.bean.request.WxPayOrderQueryRequest
import com.github.binarywang.wxpay.bean.request.WxPayOrderQueryV3Request
import com.github.binarywang.wxpay.bean.request.WxPayRefundQueryRequest
import com.github.binarywang.wxpay.bean.request.WxPayRefundQueryV3Request
import com.github.binarywang.wxpay.bean.request.WxPayRefundRequest
import com.github.binarywang.wxpay.bean.request.WxPayRefundV3Request
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderRequest
import com.github.binarywang.wxpay.bean.request.WxPayUnifiedOrderV3Request
import com.github.binarywang.wxpay.bean.order.WxPayAppOrderResult
import com.github.binarywang.wxpay.bean.result.WxPayMicropayResult
import com.github.binarywang.wxpay.bean.order.WxPayMwebOrderResult
import com.github.binarywang.wxpay.bean.order.WxPayNativeOrderResult
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryResult
import com.github.binarywang.wxpay.bean.result.WxPayOrderQueryV3Result
import com.github.binarywang.wxpay.bean.result.WxPayRefundQueryResult
import com.github.binarywang.wxpay.bean.result.WxPayRefundQueryV3Result
import com.github.binarywang.wxpay.bean.result.WxPayRefundResult
import com.github.binarywang.wxpay.bean.result.WxPayRefundV3Result
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderV3Result
import com.github.binarywang.wxpay.bean.result.WxPayUnifiedOrderResult
import com.github.binarywang.wxpay.bean.result.enums.TradeTypeEnum
import com.github.binarywang.wxpay.bean.transfer.TransferBillsGetResult
import com.github.binarywang.wxpay.bean.transfer.TransferBillsNotifyResult
import com.github.binarywang.wxpay.bean.transfer.TransferBillsRequest
import com.github.binarywang.wxpay.bean.transfer.TransferBillsResult
import com.github.binarywang.wxpay.config.WxPayConfig
import com.github.binarywang.wxpay.constant.WxPayConstants
import com.github.binarywang.wxpay.exception.WxPayException
import com.github.binarywang.wxpay.service.WxPayService
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

/** WeChat Pay V2/V3 adapter covering JSAPI, App, Native, H5 and micropay. */
class WxPayClient(
    override val id: Long,
    private val channelCode: String,
    override var config: WxPayClientConfig,
) : PayClient<WxPayClientConfig> {
    @Volatile
    private var client: WxPayService? = null

    override fun unifiedOrder(reqDTO: PayOrderUnifiedReqDTO): PayOrderRespDTO = try {
        when (config.apiVersion) {
            WxPayClientConfig.API_VERSION_V2 -> unifiedOrderV2(reqDTO)
            WxPayClientConfig.API_VERSION_V3 -> unifiedOrderV3(reqDTO)
            else -> throw IllegalArgumentException("Unknown WeChat API version: ${config.apiVersion}")
        }
    } catch (ex: WxPayException) {
        PayOrderRespDTO.closedOf(errorCode(ex), errorMessage(ex), reqDTO.outTradeNo, ex)
    }

    override fun parseOrderNotify(
        params: Map<String, String>,
        body: String,
        headers: Map<String, String>,
    ): PayOrderRespDTO = when (config.apiVersion) {
        WxPayClientConfig.API_VERSION_V2 -> {
            val result: WxPayOrderNotifyResult = service().parseOrderNotifyResult(body)
            PayOrderRespDTO.of(
                if (result.resultCode == "SUCCESS") PayOrderStatusEnum.SUCCESS.status else PayOrderStatusEnum.CLOSED.status,
                result.transactionId,
                result.openid,
                parseV2(result.timeEnd),
                result.outTradeNo,
                body,
            )
        }
        WxPayClientConfig.API_VERSION_V3 -> {
            val result = service().parseOrderNotifyV3Result(body, signatureHeader(headers)).result
            PayOrderRespDTO.of(
                parseTradeState(result.tradeState),
                result.transactionId,
                result.payer?.openid,
                parseV3(result.successTime),
                result.outTradeNo,
                body,
            )
        }
        else -> throw IllegalArgumentException("Unknown WeChat API version: ${config.apiVersion}")
    }

    override fun getOrder(outTradeNo: String): PayOrderRespDTO = try {
        when (config.apiVersion) {
            WxPayClientConfig.API_VERSION_V2 -> {
                val result: WxPayOrderQueryResult = service().queryOrder(
                    WxPayOrderQueryRequest.newBuilder().outTradeNo(outTradeNo).build(),
                )
                PayOrderRespDTO.of(parseTradeState(result.tradeState), result.transactionId, result.openid, parseV2(result.timeEnd), outTradeNo, result)
            }
            WxPayClientConfig.API_VERSION_V3 -> {
                val result: WxPayOrderQueryV3Result = service().queryOrderV3(WxPayOrderQueryV3Request().setOutTradeNo(outTradeNo))
                PayOrderRespDTO.of(parseTradeState(result.tradeState), result.transactionId, result.payer?.openid, parseV3(result.successTime), outTradeNo, result)
            }
            else -> throw IllegalArgumentException("Unknown WeChat API version: ${config.apiVersion}")
        }
    } catch (ex: WxPayException) {
        if (ex.errCode == "ORDERNOTEXIST" || ex.errCode == "ORDER_NOT_EXIST") {
            PayOrderRespDTO.closedOf(errorCode(ex), errorMessage(ex), outTradeNo, ex)
        } else {
            throw ex
        }
    }

    override fun unifiedRefund(reqDTO: PayRefundUnifiedReqDTO): PayRefundRespDTO = try {
        when (config.apiVersion) {
            WxPayClientConfig.API_VERSION_V2 -> {
                val request = WxPayRefundRequest.newBuilder()
                    .outTradeNo(reqDTO.outTradeNo)
                    .outRefundNo(reqDTO.outRefundNo)
                    .refundFee(reqDTO.refundPrice)
                    .refundDesc(reqDTO.reason)
                    .totalFee(reqDTO.payPrice)
                    .notifyUrl(reqDTO.notifyUrl)
                    .build()
                val result: WxPayRefundResult = service().refundV2(request)
                if (result.resultCode == "SUCCESS") PayRefundRespDTO.waitingOf(result.refundId, reqDTO.outRefundNo, result)
                else PayRefundRespDTO.failureOf(result.errCode, result.errCodeDes, reqDTO.outRefundNo, result)
            }
            WxPayClientConfig.API_VERSION_V3 -> {
                val request = WxPayRefundV3Request()
                    .setOutTradeNo(reqDTO.outTradeNo)
                    .setOutRefundNo(reqDTO.outRefundNo)
                    .setAmount(
                        WxPayRefundV3Request.Amount()
                            .setRefund(reqDTO.refundPrice)
                            .setTotal(reqDTO.payPrice)
                            .setCurrency("CNY"),
                    )
                    .setReason(reqDTO.reason)
                    .setNotifyUrl(reqDTO.notifyUrl)
                val result: WxPayRefundV3Result = service().refundV3(request)
                when (result.status) {
                    "SUCCESS" -> PayRefundRespDTO.successOf(result.refundId, parseV3(result.successTime), reqDTO.outRefundNo, result)
                    "PROCESSING" -> PayRefundRespDTO.waitingOf(result.refundId, reqDTO.outRefundNo, result)
                    else -> PayRefundRespDTO.failureOf(reqDTO.outRefundNo, result)
                }
            }
            else -> throw IllegalArgumentException("Unknown WeChat API version: ${config.apiVersion}")
        }
    } catch (ex: WxPayException) {
        PayRefundRespDTO.failureOf(errorCode(ex), errorMessage(ex), reqDTO.outRefundNo, ex)
    }

    override fun parseRefundNotify(
        params: Map<String, String>,
        body: String,
        headers: Map<String, String>,
    ): PayRefundRespDTO = when (config.apiVersion) {
        WxPayClientConfig.API_VERSION_V2 -> {
            val result: WxPayRefundNotifyResult = service().parseRefundNotifyResult(body)
            val info = result.reqInfo
            if (info.refundStatus == "SUCCESS") PayRefundRespDTO.successOf(info.refundId, parseV2Basic(info.successTime), info.outRefundNo, result)
            else PayRefundRespDTO.failureOf(info.outRefundNo, result)
        }
        WxPayClientConfig.API_VERSION_V3 -> {
            val result: WxPayRefundNotifyV3Result = service().parseRefundNotifyV3Result(body, signatureHeader(headers))
            val info = result.result
            if (info.refundStatus == "SUCCESS") PayRefundRespDTO.successOf(info.refundId, parseV3(info.successTime), info.outRefundNo, result)
            else PayRefundRespDTO.failureOf(info.outRefundNo, result)
        }
        else -> throw IllegalArgumentException("Unknown WeChat API version: ${config.apiVersion}")
    }

    override fun getRefund(outTradeNo: String, outRefundNo: String): PayRefundRespDTO = try {
        when (config.apiVersion) {
            WxPayClientConfig.API_VERSION_V2 -> {
                val result: WxPayRefundQueryResult = service().refundQuery(
                    WxPayRefundQueryRequest.newBuilder().outTradeNo(outTradeNo).outRefundNo(outRefundNo).build(),
                )
                if (result.resultCode != "SUCCESS") PayRefundRespDTO.waitingOf(null, outRefundNo, result)
                else {
                    val item = result.refundRecords.orEmpty().firstOrNull { it.outRefundNo == outRefundNo }
                    when (item?.refundStatus) {
                        "SUCCESS" -> PayRefundRespDTO.successOf(item.refundId, parseV2Basic(item.refundSuccessTime), outRefundNo, result)
                        "PROCESSING" -> PayRefundRespDTO.waitingOf(item.refundId, outRefundNo, result)
                        "CHANGE", "FAIL" -> PayRefundRespDTO.failureOf(outRefundNo, result)
                        else -> PayRefundRespDTO.failureOf(outRefundNo, result)
                    }
                }
            }
            WxPayClientConfig.API_VERSION_V3 -> {
                val query = WxPayRefundQueryV3Request().apply { setOutRefundNo(outRefundNo) }
                val result: WxPayRefundQueryV3Result = service().refundQueryV3(query)
                when (result.status) {
                    "SUCCESS" -> PayRefundRespDTO.successOf(result.refundId, parseV3(result.successTime), outRefundNo, result)
                    "PROCESSING" -> PayRefundRespDTO.waitingOf(result.refundId, outRefundNo, result)
                    else -> PayRefundRespDTO.failureOf(outRefundNo, result)
                }
            }
            else -> throw IllegalArgumentException("Unknown WeChat API version: ${config.apiVersion}")
        }
    } catch (ex: WxPayException) {
        if (ex.errCode == "REFUNDNOTEXIST" || ex.errCode == "RESOURCE_NOT_EXISTS") {
            PayRefundRespDTO.failureOf(errorCode(ex), errorMessage(ex), outRefundNo, ex)
        } else throw ex
    }

    override fun unifiedTransfer(reqDTO: PayTransferUnifiedReqDTO): PayTransferRespDTO {
        return try {
            if (config.apiVersion != WxPayClientConfig.API_VERSION_V3) {
                PayTransferRespDTO.closedOf("UNSUPPORTED_VERSION", "WeChat transfer requires API v3", reqDTO.outTransferNo, null)
            } else {
                val request = TransferBillsRequest.newBuilder()
                    .appid(config.appId)
                    .outBillNo(reqDTO.outTransferNo)
                    .transferAmount(reqDTO.price)
                    .transferRemark(reqDTO.subject)
                    .transferSceneId(reqDTO.channelExtras?.get("sceneId"))
                    .openid(reqDTO.userAccount)
                    .userName(reqDTO.userName)
                    .transferSceneReportInfos(
                        reqDTO.channelExtras?.get("sceneReportInfos")?.let {
                            JsonUtils.parseArray(it, TransferBillsRequest.TransferSceneReportInfo::class.java)
                        },
                    )
                    .notifyUrl(reqDTO.notifyUrl)
                    .build()
                if ((reqDTO.price ?: 0) < 30) request.userName = null
                val result: TransferBillsResult = service().transferService.transferBills(request)
                when (result.state) {
                    "ACCEPTED", "PROCESSING", "WAIT_USER_CONFIRM", "TRANSFERING" ->
                        PayTransferRespDTO.processingOf(result.transferBillNo, result.outBillNo, result)
                            .setChannelPackageInfo(result.packageInfo)
                    "SUCCESS" -> PayTransferRespDTO.successOf(result.transferBillNo, parseV3(result.createTime), result.outBillNo, result)
                    else -> PayTransferRespDTO.closedOf(result.state, result.failReason, result.outBillNo, result)
                }
            }
        } catch (ex: WxPayException) {
            PayTransferRespDTO.closedOf(errorCode(ex), errorMessage(ex), reqDTO.outTransferNo, ex)
        }
    }

    override fun getTransfer(outTradeNo: String): PayTransferRespDTO {
        if (config.apiVersion != WxPayClientConfig.API_VERSION_V3) {
            return PayTransferRespDTO.closedOf("UNSUPPORTED_VERSION", "WeChat transfer requires API v3", outTradeNo, null)
        }
        val result: TransferBillsGetResult = service().transferService.getBillsByOutBillNo(outTradeNo)
        return when (result.state) {
            "ACCEPTED", "PROCESSING", "WAIT_USER_CONFIRM", "TRANSFERING" -> PayTransferRespDTO.processingOf(result.transferBillNo, result.outBillNo, result)
            "SUCCESS" -> PayTransferRespDTO.successOf(result.transferBillNo, parseV3(result.updateTime), result.outBillNo, result)
            else -> PayTransferRespDTO.closedOf(result.state, result.failReason, result.outBillNo, result)
        }
    }

    override fun parseTransferNotify(
        params: Map<String, String>,
        body: String,
        headers: Map<String, String>,
    ): PayTransferRespDTO {
        if (config.apiVersion != WxPayClientConfig.API_VERSION_V3) {
            throw UnsupportedOperationException("WeChat V2 transfer callbacks are unsupported")
        }
        val result: TransferBillsNotifyResult = service().transferService.parseTransferBillsNotifyResult(body, signatureHeader(headers))
        val info = result.result
        return when (info.state) {
            "ACCEPTED", "PROCESSING", "WAIT_USER_CONFIRM", "TRANSFERING" -> PayTransferRespDTO.processingOf(info.transferBillNo, info.outBillNo, result)
            "SUCCESS" -> PayTransferRespDTO.successOf(info.transferBillNo, parseV3(info.updateTime), info.outBillNo, result)
            else -> PayTransferRespDTO.closedOf(info.state, info.failReason, info.outBillNo, result)
        }
    }

    fun refresh(newConfig: WxPayClientConfig) {
        config = newConfig
        client = null
    }

    private fun unifiedOrderV2(req: PayOrderUnifiedReqDTO): PayOrderRespDTO {
        val base = WxPayUnifiedOrderRequest.newBuilder()
            .outTradeNo(req.outTradeNo)
            .body(req.subject)
            .detail(req.body)
            .totalFee(req.price)
            .timeExpire(formatV2(req.expireTime))
            .spbillCreateIp(req.userIp)
            .notifyUrl(req.notifyUrl)
        return when (channelCode) {
            PayChannelEnum.WX_PUB.code, PayChannelEnum.WX_LITE.code -> {
                val result: com.github.binarywang.wxpay.bean.order.WxPayMpOrderResult = service().createOrder(base.openid(requireOpenid(req)).build())
                PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.APP.mode, JsonUtils.toJsonString(result), req.outTradeNo, result)
            }
            PayChannelEnum.WX_APP.code -> {
                val result: WxPayAppOrderResult = service().createOrder(base.build())
                PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.APP.mode, JsonUtils.toJsonString(result), req.outTradeNo, result)
            }
            PayChannelEnum.WX_NATIVE.code -> {
                val result: WxPayNativeOrderResult = service().createOrder(base.productId(req.outTradeNo).build())
                PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.QR_CODE.mode, result.codeUrl, req.outTradeNo, result)
            }
            PayChannelEnum.WX_WAP.code -> {
                val result: WxPayMwebOrderResult = service().createOrder(base.build())
                PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.URL.mode, result.mwebUrl, req.outTradeNo, result)
            }
            PayChannelEnum.WX_BAR.code -> unifiedBar(req)
            else -> throw IllegalArgumentException("Unsupported WeChat channel: $channelCode")
        }
    }

    private fun unifiedOrderV3(req: PayOrderUnifiedReqDTO): PayOrderRespDTO {
        if (channelCode == PayChannelEnum.WX_BAR.code) return unifiedBar(req)
        val base = WxPayUnifiedOrderV3Request()
            .setOutTradeNo(req.outTradeNo)
            .setDescription(req.subject)
            .setAmount(WxPayUnifiedOrderV3Request.Amount().setTotal(req.price))
            .setTimeExpire(formatV3(req.expireTime))
            .setSceneInfo(WxPayUnifiedOrderV3Request.SceneInfo().setPayerClientIp(req.userIp))
            .setNotifyUrl(req.notifyUrl)
        return when (channelCode) {
            PayChannelEnum.WX_PUB.code, PayChannelEnum.WX_LITE.code -> {
                val result: WxPayUnifiedOrderV3Result.JsapiResult = service().createOrderV3(TradeTypeEnum.JSAPI, base.setPayer(WxPayUnifiedOrderV3Request.Payer().setOpenid(requireOpenid(req))))
                PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.APP.mode, JsonUtils.toJsonString(result), req.outTradeNo, result)
            }
            PayChannelEnum.WX_APP.code -> {
                val result: WxPayUnifiedOrderV3Result.AppResult = service().createOrderV3(TradeTypeEnum.APP, base)
                PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.APP.mode, JsonUtils.toJsonString(result), req.outTradeNo, result)
            }
            PayChannelEnum.WX_NATIVE.code -> {
                val result: String = service().createOrderV3(TradeTypeEnum.NATIVE, base)
                PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.QR_CODE.mode, result, req.outTradeNo, result)
            }
            PayChannelEnum.WX_WAP.code -> {
                val result: String = service().createOrderV3(TradeTypeEnum.H5, base)
                PayOrderRespDTO.waitingOf(PayOrderDisplayModeEnum.URL.mode, result, req.outTradeNo, result)
            }
            else -> throw IllegalArgumentException("Unsupported WeChat channel: $channelCode")
        }
    }

    private fun unifiedBar(req: PayOrderUnifiedReqDTO): PayOrderRespDTO {
        val request = WxPayMicropayRequest.newBuilder()
            .outTradeNo(req.outTradeNo)
            .body(req.subject)
            .detail(req.body)
            .totalFee(req.price)
            .timeExpire(formatV2(req.expireTime))
            .spbillCreateIp(req.userIp)
            .authCode(req.channelExtras?.get("authCode") ?: req.channelExtras?.get("auth_code"))
            .build()
        val result: WxPayMicropayResult = service().micropay(request)
        return PayOrderRespDTO.successOf(result.transactionId, result.openid, parseV2(result.timeEnd), result.outTradeNo, result)
            .setDisplayMode(PayOrderDisplayModeEnum.BAR_CODE.mode)
    }

    private fun service(): WxPayService {
        client?.let { return it }
        synchronized(this) {
            client?.let { return it }
            val payConfig = WxPayConfig().apply {
                appId = config.appId
                mchId = config.mchId
                apiV3Key = config.apiV3Key
                certSerialNo = config.certSerialNo
                publicKeyId = config.publicKeyId
                mchKey = config.mchKey
                tradeType = tradeType()
                if (config.apiVersion == WxPayClientConfig.API_VERSION_V2) {
                    keyPath = FileUtils.createTempFile(Base64.getDecoder().decode(requireNotNull(config.keyContent))).path
                } else {
                    privateKeyPath = FileUtils.createTempFile(requireNotNull(config.privateKeyContent)).path
                    config.publicKeyContent?.takeIf(String::isNotBlank)?.let {
                        publicKeyPath = FileUtils.createTempFile(it).path
                    }
                    setStrictlyNeedWechatPaySerial(true)
                    setFullPublicKeyModel(true)
                }
            }
            return WxPayServiceImpl().also {
                it.setConfig(payConfig)
                client = it
            }
        }
    }

    private fun tradeType(): String = when (channelCode) {
        PayChannelEnum.WX_PUB.code, PayChannelEnum.WX_LITE.code -> WxPayConstants.TradeType.JSAPI
        PayChannelEnum.WX_APP.code -> WxPayConstants.TradeType.APP
        PayChannelEnum.WX_NATIVE.code -> WxPayConstants.TradeType.NATIVE
        PayChannelEnum.WX_WAP.code -> WxPayConstants.TradeType.MWEB
        PayChannelEnum.WX_BAR.code -> WxPayConstants.TradeType.MICROPAY
        else -> WxPayConstants.TradeType.JSAPI
    }

    private fun requireOpenid(req: PayOrderUnifiedReqDTO): String = req.channelExtras?.get("openid")?.takeIf(String::isNotBlank)
        ?: throw IllegalArgumentException("openid is required for WeChat JSAPI payment")

    private fun signatureHeader(headers: Map<String, String>) = SignatureHeader.builder()
        .signature(headers["Wechatpay-Signature"] ?: headers["wechatpay-signature"])
        .nonce(headers["Wechatpay-Nonce"] ?: headers["wechatpay-nonce"])
        .serial(headers["Wechatpay-Serial"] ?: headers["wechatpay-serial"])
        .timeStamp(headers["Wechatpay-Timestamp"] ?: headers["wechatpay-timestamp"])
        .build()

    private fun parseTradeState(value: String?): Int = when (value) {
        "NOTPAY", "USERPAYING" -> PayOrderStatusEnum.WAITING.status
        "SUCCESS" -> PayOrderStatusEnum.SUCCESS.status
        "REFUND" -> PayOrderStatusEnum.REFUND.status
        "CLOSED", "REVOKED", "PAYERROR" -> PayOrderStatusEnum.CLOSED.status
        else -> throw IllegalArgumentException("Unknown WeChat trade state: $value")
    }

    private fun formatV2(value: LocalDateTime?): String? = value?.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))

    private fun formatV3(value: LocalDateTime?): String? = value?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun parseV2(value: String?): LocalDateTime? = value?.takeIf(String::isNotBlank)?.let {
        runCatching { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) }.getOrNull()
    }

    private fun parseV2Basic(value: String?): LocalDateTime? = value?.takeIf(String::isNotBlank)?.let {
        runCatching { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) }.getOrNull()
    }

    private fun parseV3(value: String?): LocalDateTime? = value?.takeIf(String::isNotBlank)?.let {
        runCatching { java.time.OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime() }.getOrNull()
    }

    private fun errorCode(ex: WxPayException): String = ex.errCode?.takeIf(String::isNotBlank) ?: ex.returnCode ?: "WX_ERROR"

    private fun errorMessage(ex: WxPayException): String = ex.errCodeDes?.takeIf(String::isNotBlank) ?: ex.customErrorMsg ?: ex.returnMsg ?: ex.message.orEmpty()
}
