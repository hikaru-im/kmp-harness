package im.hikaru.ruoyi.framework.common.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class MobileValidator : ConstraintValidator<Mobile, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value.isNullOrEmpty() || MOBILE_PATTERN.matches(value)

    companion object {
        private val MOBILE_PATTERN = Regex("^1[3-9]\\d{9}$")
    }
}
