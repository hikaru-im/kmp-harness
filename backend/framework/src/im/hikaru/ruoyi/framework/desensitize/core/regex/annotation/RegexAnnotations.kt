package im.hikaru.ruoyi.framework.desensitize.core.regex.annotation

import im.hikaru.ruoyi.framework.desensitize.core.base.annotation.DesensitizeBy
import im.hikaru.ruoyi.framework.desensitize.core.regex.handler.DefaultRegexDesensitizationHandler
import im.hikaru.ruoyi.framework.desensitize.core.regex.handler.EmailDesensitizationHandler
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = DefaultRegexDesensitizationHandler::class)
annotation class RegexDesensitize(
    val regex: String = "^[\\s\\S]*$",
    val replacer: String = "******",
    val disable: String = "",
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = EmailDesensitizationHandler::class)
annotation class EmailDesensitize(
    val regex: String = "(^.)[^@]*(@.*$)",
    val replacer: String = "\$1****\$2",
    val disable: String = "",
)
