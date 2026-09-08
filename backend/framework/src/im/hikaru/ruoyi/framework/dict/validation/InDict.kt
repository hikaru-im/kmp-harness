package im.hikaru.ruoyi.framework.dict.validation

import im.hikaru.ruoyi.framework.dict.validation.InDictValidator
import im.hikaru.ruoyi.framework.dict.validation.InDictCollectionValidator
import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

/**
 * 数据字典校验注解 (迁移自 Java)
 *
 * @author 芋道源码
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.VALUE_PARAMETER,
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [InDictValidator::class, InDictCollectionValidator::class])
annotation class InDict(
    /** 数据字典 type */
    val type: String,
    val message: String = "必须在指定范围 {value}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
