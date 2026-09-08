package im.hikaru.ruoyi.framework.common.validation

import im.hikaru.ruoyi.framework.common.util.validation.ValidationUtils
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class TelephoneValidator : ConstraintValidator<Telephone, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        if (value.isNullOrEmpty()) return true
        return ValidationUtils.isMobile(value) || LANDLINE.matches(value) || SERVICE_NUMBER.matches(value)
    }

    companion object {
        private val LANDLINE = Regex("^(?:0\\d{2,3}-?)?\\d{7,8}(?:-\\d{1,6})?$")
        private val SERVICE_NUMBER = Regex("^(?:400|800)-?\\d{3}-?\\d{4}$")
    }
}
