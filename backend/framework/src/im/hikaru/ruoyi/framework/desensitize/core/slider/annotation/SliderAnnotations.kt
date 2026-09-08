package im.hikaru.ruoyi.framework.desensitize.core.slider.annotation

import im.hikaru.ruoyi.framework.desensitize.core.base.annotation.DesensitizeBy
import im.hikaru.ruoyi.framework.desensitize.core.slider.handler.BankCardDesensitization
import im.hikaru.ruoyi.framework.desensitize.core.slider.handler.CarLicenseDesensitization
import im.hikaru.ruoyi.framework.desensitize.core.slider.handler.ChineseNameDesensitization
import im.hikaru.ruoyi.framework.desensitize.core.slider.handler.DefaultDesensitizationHandler
import im.hikaru.ruoyi.framework.desensitize.core.slider.handler.FixedPhoneDesensitization
import im.hikaru.ruoyi.framework.desensitize.core.slider.handler.IdCardDesensitization
import im.hikaru.ruoyi.framework.desensitize.core.slider.handler.MobileDesensitization
import im.hikaru.ruoyi.framework.desensitize.core.slider.handler.PasswordDesensitization
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = DefaultDesensitizationHandler::class)
annotation class SliderDesensitize(
    val suffixKeep: Int = 0,
    val replacer: String = "*",
    val prefixKeep: Int = 0,
    val disable: String = "",
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = BankCardDesensitization::class)
annotation class BankCardDesensitize(
    val prefixKeep: Int = 6,
    val suffixKeep: Int = 2,
    val replacer: String = "*",
    val disable: String = "",
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = CarLicenseDesensitization::class)
annotation class CarLicenseDesensitize(
    val prefixKeep: Int = 3,
    val suffixKeep: Int = 1,
    val replacer: String = "*",
    val disable: String = "",
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = ChineseNameDesensitization::class)
annotation class ChineseNameDesensitize(
    val prefixKeep: Int = 1,
    val suffixKeep: Int = 0,
    val replacer: String = "*",
    val disable: String = "",
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = FixedPhoneDesensitization::class)
annotation class FixedPhoneDesensitize(
    val prefixKeep: Int = 4,
    val suffixKeep: Int = 2,
    val replacer: String = "*",
    val disable: String = "",
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = IdCardDesensitization::class)
annotation class IdCardDesensitize(
    val prefixKeep: Int = 6,
    val suffixKeep: Int = 2,
    val replacer: String = "*",
    val disable: String = "",
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = MobileDesensitization::class)
annotation class MobileDesensitize(
    val prefixKeep: Int = 3,
    val suffixKeep: Int = 4,
    val replacer: String = "*",
    val disable: String = "",
)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER)
@Retention(AnnotationRetention.RUNTIME)
@JacksonAnnotationsInside
@DesensitizeBy(handler = PasswordDesensitization::class)
annotation class PasswordDesensitize(
    val prefixKeep: Int = 0,
    val suffixKeep: Int = 0,
    val replacer: String = "*",
    val disable: String = "",
)
