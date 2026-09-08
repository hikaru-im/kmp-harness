package im.hikaru.ruoyi.framework.desensitize.core.slider.handler

import im.hikaru.ruoyi.framework.common.util.spring.SpringExpressionUtils
import im.hikaru.ruoyi.framework.desensitize.core.base.handler.DesensitizationHandler
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.BankCardDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.CarLicenseDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.ChineseNameDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.FixedPhoneDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.IdCardDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.MobileDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.PasswordDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.slider.annotation.SliderDesensitize
abstract class AbstractSliderDesensitizationHandler<A : Annotation> : DesensitizationHandler<A> {

    final override fun desensitize(origin: String, annotation: A): String {
        if (SpringExpressionUtils.parseExpression(getDisable(annotation)) == true) return origin

        val prefixKeep = getPrefixKeep(annotation)
        val suffixKeep = getSuffixKeep(annotation)
        val interval = origin.length - prefixKeep - suffixKeep
        val replacer = getReplacer(annotation)
        if (interval <= 0) return replacer.repeat(origin.length)
        return origin.substring(0, prefixKeep) +
            replacer.repeat(interval) +
            origin.substring(prefixKeep + interval)
    }

    protected abstract fun getPrefixKeep(annotation: A): Int

    protected abstract fun getSuffixKeep(annotation: A): Int

    protected abstract fun getReplacer(annotation: A): String
}

class DefaultDesensitizationHandler : AbstractSliderDesensitizationHandler<SliderDesensitize>() {
    override fun getPrefixKeep(annotation: SliderDesensitize): Int = annotation.prefixKeep
    override fun getSuffixKeep(annotation: SliderDesensitize): Int = annotation.suffixKeep
    override fun getReplacer(annotation: SliderDesensitize): String = annotation.replacer
}

class BankCardDesensitization : AbstractSliderDesensitizationHandler<BankCardDesensitize>() {
    override fun getPrefixKeep(annotation: BankCardDesensitize): Int = annotation.prefixKeep
    override fun getSuffixKeep(annotation: BankCardDesensitize): Int = annotation.suffixKeep
    override fun getReplacer(annotation: BankCardDesensitize): String = annotation.replacer
    override fun getDisable(annotation: BankCardDesensitize): String = ""
}

class CarLicenseDesensitization : AbstractSliderDesensitizationHandler<CarLicenseDesensitize>() {
    override fun getPrefixKeep(annotation: CarLicenseDesensitize): Int = annotation.prefixKeep
    override fun getSuffixKeep(annotation: CarLicenseDesensitize): Int = annotation.suffixKeep
    override fun getReplacer(annotation: CarLicenseDesensitize): String = annotation.replacer
    override fun getDisable(annotation: CarLicenseDesensitize): String = annotation.disable
}

class ChineseNameDesensitization : AbstractSliderDesensitizationHandler<ChineseNameDesensitize>() {
    override fun getPrefixKeep(annotation: ChineseNameDesensitize): Int = annotation.prefixKeep
    override fun getSuffixKeep(annotation: ChineseNameDesensitize): Int = annotation.suffixKeep
    override fun getReplacer(annotation: ChineseNameDesensitize): String = annotation.replacer
}

class FixedPhoneDesensitization : AbstractSliderDesensitizationHandler<FixedPhoneDesensitize>() {
    override fun getPrefixKeep(annotation: FixedPhoneDesensitize): Int = annotation.prefixKeep
    override fun getSuffixKeep(annotation: FixedPhoneDesensitize): Int = annotation.suffixKeep
    override fun getReplacer(annotation: FixedPhoneDesensitize): String = annotation.replacer
}

class IdCardDesensitization : AbstractSliderDesensitizationHandler<IdCardDesensitize>() {
    override fun getPrefixKeep(annotation: IdCardDesensitize): Int = annotation.prefixKeep
    override fun getSuffixKeep(annotation: IdCardDesensitize): Int = annotation.suffixKeep
    override fun getReplacer(annotation: IdCardDesensitize): String = annotation.replacer
}

class MobileDesensitization : AbstractSliderDesensitizationHandler<MobileDesensitize>() {
    override fun getPrefixKeep(annotation: MobileDesensitize): Int = annotation.prefixKeep
    override fun getSuffixKeep(annotation: MobileDesensitize): Int = annotation.suffixKeep
    override fun getReplacer(annotation: MobileDesensitize): String = annotation.replacer
}

class PasswordDesensitization : AbstractSliderDesensitizationHandler<PasswordDesensitize>() {
    override fun getPrefixKeep(annotation: PasswordDesensitize): Int = annotation.prefixKeep
    override fun getSuffixKeep(annotation: PasswordDesensitize): Int = annotation.suffixKeep
    override fun getReplacer(annotation: PasswordDesensitize): String = annotation.replacer
}
