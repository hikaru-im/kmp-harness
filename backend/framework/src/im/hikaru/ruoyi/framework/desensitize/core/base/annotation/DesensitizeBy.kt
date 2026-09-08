package im.hikaru.ruoyi.framework.desensitize.core.base.annotation

import im.hikaru.ruoyi.framework.desensitize.core.base.handler.DesensitizationHandler
import im.hikaru.ruoyi.framework.desensitize.core.base.serializer.StringDesensitizeSerializer
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside
import tools.jackson.databind.annotation.JsonSerialize
import kotlin.reflect.KClass

/** Meta-annotation for custom string desensitization annotations. */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = StringDesensitizeSerializer::class)
annotation class DesensitizeBy(
    val handler: KClass<out DesensitizationHandler<*>>,
)
