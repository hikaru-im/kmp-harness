package im.hikaru.ruoyi.module.pay.enums

import im.hikaru.ruoyi.framework.common.core.ArrayValuable

enum class PayChannelEnum(val code: String, val displayName: String) : ArrayValuable<String> {
    WX_PUB("wx_pub", "微信 JSAPI 支付"),
    WX_LITE("wx_lite", "微信小程序支付"),
    WX_APP("wx_app", "微信 App 支付"),
    WX_NATIVE("wx_native", "微信 Native 支付"),
    WX_WAP("wx_wap", "微信 Wap 网站支付"),
    WX_BAR("wx_bar", "微信付款码支付"),
    ALIPAY_PC("alipay_pc", "支付宝 PC 网站支付"),
    ALIPAY_WAP("alipay_wap", "支付宝 Wap 网站支付"),
    ALIPAY_APP("alipay_app", "支付宝App 支付"),
    ALIPAY_QR("alipay_qr", "支付宝扫码支付"),
    ALIPAY_BAR("alipay_bar", "支付宝条码支付"),
    MOCK("mock", "模拟支付"),
    WALLET("wallet", "钱包支付");

    override fun array(): Array<String> = ARRAYS

    companion object {
        val ARRAYS: Array<String> = entries.map { it.code }.toTypedArray()
        fun getByCode(code: String?): PayChannelEnum? = entries.firstOrNull { it.code == code }
        fun isAlipay(channelCode: String?): Boolean = channelCode?.startsWith("alipay_") == true
        fun isWeixin(channelCode: String?): Boolean = channelCode?.startsWith("wx_") == true
    }
}
