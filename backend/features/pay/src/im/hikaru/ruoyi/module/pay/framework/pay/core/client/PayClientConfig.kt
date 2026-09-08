package im.hikaru.ruoyi.module.pay.framework.pay.core.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jakarta.validation.Validator

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
interface PayClientConfig {
    fun validate(validator: Validator)
}
