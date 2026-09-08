package im.hikaru.ruoyi.framework.dict.validation

import im.hikaru.ruoyi.framework.dict.core.DictFrameworkUtils
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

/**
 * 单个字典值校验器 (迁移自 Java, 去 Hutool StrUtil)
 *
 * 迁移说明：Hutool StrUtil.equalsIgnoreCase → equals(other, ignoreCase = true)
 */
class InDictValidator : ConstraintValidator<InDict, Any> {

    private lateinit var dictType: String

    override fun initialize(annotation: InDict) {
        this.dictType = annotation.type
    }

    override fun isValid(value: Any?, context: ConstraintValidatorContext): Boolean {
        // 为空时，默认不校验
        if (value == null) {
            return true
        }
        val values = DictFrameworkUtils.getDictDataValueList(dictType)
        val match = values.any { it.equals(value.toString(), ignoreCase = true) }
        if (match) {
            return true
        }
        // 校验不通过，自定义提示
        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(
            context.defaultConstraintMessageTemplate.replace("{value}", values.toString()),
        ).addConstraintViolation()
        return false
    }
}
