package im.hikaru.ruoyi.framework.dict.validation

import im.hikaru.ruoyi.framework.dict.core.DictFrameworkUtils
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

/**
 * 字典集合校验器 (迁移自 Java, 去 Hutool CollUtil)
 */
class InDictCollectionValidator : ConstraintValidator<InDict, Collection<*>> {

    private lateinit var dictType: String

    override fun initialize(annotation: InDict) {
        this.dictType = annotation.type
    }

    override fun isValid(list: Collection<*>?, context: ConstraintValidatorContext): Boolean {
        // 为空时，默认不校验
        if (list.isNullOrEmpty()) {
            return true
        }
        val dbValues = DictFrameworkUtils.getDictDataValueList(dictType)
        val match = list.all { v ->
            dbValues.any { dbValue -> dbValue.equals(v.toString(), ignoreCase = true) }
        }
        if (match) {
            return true
        }
        context.disableDefaultConstraintViolation()
        context.buildConstraintViolationWithTemplate(
            context.defaultConstraintMessageTemplate.replace("{value}", dbValues.toString()),
        ).addConstraintViolation()
        return false
    }
}
