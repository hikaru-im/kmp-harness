package im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl.weixin

import im.hikaru.ruoyi.framework.common.util.validation.ValidationUtils
import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientConfig
import jakarta.validation.Validator
import jakarta.validation.constraints.NotBlank

class WxPayClientConfig : PayClientConfig {
    @field:NotBlank(message = "APPID 不能为空", groups = [V2::class, V3::class])
    var appId: String? = null
    @field:NotBlank(message = "商户号不能为空", groups = [V2::class, V3::class])
    var mchId: String? = null
    @field:NotBlank(message = "API 版本不能为空", groups = [V2::class, V3::class])
    var apiVersion: String? = null
    @field:NotBlank(message = "商户密钥不能为空", groups = [V2::class])
    var mchKey: String? = null
    @field:NotBlank(message = "apiclient_cert.p12 不能为空", groups = [V2::class])
    var keyContent: String? = null
    @field:NotBlank(message = "apiclient_key 不能为空", groups = [V3::class])
    var privateKeyContent: String? = null
    @field:NotBlank(message = "apiV3 密钥不能为空", groups = [V3::class])
    var apiV3Key: String? = null
    @field:NotBlank(message = "证书序列号不能为空", groups = [V3::class])
    var certSerialNo: String? = null
    var publicKeyContent: String? = null
    @field:NotBlank(message = "publicKeyId 不能为空", groups = [V3::class])
    var publicKeyId: String? = null

    override fun validate(validator: Validator) {
        ValidationUtils.validate(validator, this, if (apiVersion == API_VERSION_V2) V2::class.java else V3::class.java)
    }

    interface V2
    interface V3

    companion object {
        const val API_VERSION_V2 = "v2"
        const val API_VERSION_V3 = "v3"
    }
}
