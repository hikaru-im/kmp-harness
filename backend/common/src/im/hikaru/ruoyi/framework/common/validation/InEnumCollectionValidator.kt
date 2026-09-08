package im.hikaru.ruoyi.framework.common.validation

import im.hikaru.ruoyi.framework.common.core.ArrayValuable
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class InEnumCollectionValidator : ConstraintValidator<InEnum, Collection<*>> {
    private var values: List<Any?> = emptyList()

    override fun initialize(annotation: InEnum) {
        values = annotation.value.java.enumConstants
            ?.firstOrNull()
            ?.let { (it as ArrayValuable<*>).array().toList() }
            .orEmpty()
    }

    override fun isValid(value: Collection<*>?, context: ConstraintValidatorContext): Boolean {
        if (value == null || values.containsAll(value)) return true
        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(
            context.defaultConstraintMessageTemplate.replace("{value}", values.toString()),
        ).addConstraintViolation()
        return false
    }
}
