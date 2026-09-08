package im.hikaru.ruoyi.framework.common.util.validation

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import jakarta.validation.Validator

object ValidationUtils {

    private val validator: Validator by lazy {
        Validation.buildDefaultValidatorFactory().validator
    }

    @JvmStatic
    fun isMobile(mobile: String?): Boolean =
        !mobile.isNullOrBlank() && MOBILE_REGEX.matches(mobile)

    @JvmStatic
    fun isURL(url: String?): Boolean =
        !url.isNullOrBlank() && URL_REGEX.matches(url)

    @JvmStatic
    fun isXmlNCName(value: String?): Boolean =
        !value.isNullOrBlank() && XML_NCNAME_REGEX.matches(value)

    @JvmStatic
    fun validate(value: Any, vararg groups: Class<*>) {
        validate(validator, value, *groups)
    }

    @JvmStatic
    fun validate(validator: Validator, value: Any, vararg groups: Class<*>) {
        val violations = validator.validate(value, *groups)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }
    }

    private val MOBILE_REGEX = Regex(
        "^(?:(?:\\+|00)86)?1(?:(?:3[\\d])|(?:4[0,1,4-9])|(?:5[0-3,5-9])|" +
            "(?:6[2,5-7])|(?:7[0-8])|(?:8[\\d])|(?:9[0-3,5-9]))\\d{8}$",
    )
    private val URL_REGEX = Regex(
        "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
    )
    private val XML_NCNAME_REGEX = Regex("[a-zA-Z_][\\-_.0-9_a-zA-Z$]*")
}
