package im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.alipay

import im.hikaru.ruoyi.framework.common.util.validation.ValidationUtils
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientConfig
import jakarta.validation.Validator
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

class AlipayPayClientConfig : PayClientConfig {
    @field:NotBlank(message = "网关地址不能为空", groups = [ModePublicKey::class, ModeCertificate::class])
    var serverUrl: String? = null
    @field:NotBlank(message = "开放平台应用 ID 不能为空", groups = [ModePublicKey::class, ModeCertificate::class])
    var appId: String? = null
    @field:NotBlank(message = "签名算法类型不能为空", groups = [ModePublicKey::class, ModeCertificate::class])
    var signType: String? = null
    @field:NotNull(message = "公钥类型不能为空", groups = [ModePublicKey::class, ModeCertificate::class])
    var mode: Int? = null
    @field:NotBlank(message = "商户私钥不能为空", groups = [ModePublicKey::class])
    var privateKey: String? = null
    @field:NotBlank(message = "支付宝公钥不能为空", groups = [ModePublicKey::class])
    var alipayPublicKey: String? = null
    @field:NotBlank(message = "商户应用证书不能为空", groups = [ModeCertificate::class])
    var appCertContent: String? = null
    @field:NotBlank(message = "支付宝公钥证书不能为空", groups = [ModeCertificate::class])
    var alipayPublicCertContent: String? = null
    @field:NotBlank(message = "支付宝根证书不能为空", groups = [ModeCertificate::class])
    var rootCertContent: String? = null
    var encryptType: String? = null
    var encryptKey: String? = null

    override fun validate(validator: Validator) {
        ValidationUtils.validate(validator, this, if (mode == MODE_PUBLIC_KEY) ModePublicKey::class.java else ModeCertificate::class.java)
    }

    interface ModePublicKey
    interface ModeCertificate

    companion object {
        const val MODE_PUBLIC_KEY = 1
        const val MODE_CERTIFICATE = 2
        const val ENC_TYPE_AES = "AES"
        const val SIGN_TYPE_DEFAULT = "RSA2"
    }
}
