package im.hikaru.ruoyi.framework.desensitize.core.regex.handler

import im.hikaru.ruoyi.framework.common.util.spring.SpringExpressionUtils
import im.hikaru.ruoyi.framework.desensitize.core.base.handler.DesensitizationHandler
import im.hikaru.ruoyi.framework.desensitize.core.regex.annotation.EmailDesensitize
import im.hikaru.ruoyi.framework.desensitize.core.regex.annotation.RegexDesensitize
import java.util.regex.Pattern

abstract class AbstractRegexDesensitizationHandler<A : Annotation> : DesensitizationHandler<A> {

    final override fun desensitize(origin: String, annotation: A): String {
        if (SpringExpressionUtils.parseExpression(getDisable(annotation)) == true) return origin
        return Pattern.compile(getRegex(annotation)).matcher(origin).replaceAll(getReplacer(annotation))
    }

    protected abstract fun getRegex(annotation: A): String

    protected abstract fun getReplacer(annotation: A): String
}

class DefaultRegexDesensitizationHandler : AbstractRegexDesensitizationHandler<RegexDesensitize>() {
    override fun getRegex(annotation: RegexDesensitize): String = annotation.regex
    override fun getReplacer(annotation: RegexDesensitize): String = annotation.replacer
    override fun getDisable(annotation: RegexDesensitize): String = annotation.disable
}

class EmailDesensitizationHandler : AbstractRegexDesensitizationHandler<EmailDesensitize>() {
    override fun getRegex(annotation: EmailDesensitize): String = annotation.regex
    override fun getReplacer(annotation: EmailDesensitize): String = annotation.replacer
}
