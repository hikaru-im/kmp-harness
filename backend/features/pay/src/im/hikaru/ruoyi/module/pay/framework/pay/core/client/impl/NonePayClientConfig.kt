package im.hikaru.ruoyi.module.pay.framework.pay.core.client.impl

import im.hikaru.ruoyi.module.pay.framework.pay.core.client.PayClientConfig
import jakarta.validation.Validator

class NonePayClientConfig : PayClientConfig {
    var name: String = "none-config"
    override fun validate(validator: Validator) = Unit
}
