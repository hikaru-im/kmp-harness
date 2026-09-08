package im.hikaru.ruoyi.framework.common.validation

import im.hikaru.ruoyi.framework.common.core.ArrayValuable
import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.FIELD,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE,
)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [InEnumValidator::class, InEnumCollectionValidator::class])
annotation class InEnum(
    val value: KClass<out ArrayValuable<*>>,
    val message: String = "Must be one of {value}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)
