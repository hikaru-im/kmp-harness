package im.hikaru.ruoyi.framework.xss.core.json

import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.xss.config.XssProperties
import im.hikaru.ruoyi.framework.xss.core.clean.XssCleaner
import org.springframework.util.PathMatcher
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.deser.jdk.StringDeserializer

class XssStringJsonDeserializer(
    private val properties: XssProperties,
    private val pathMatcher: PathMatcher,
    private val xssCleaner: XssCleaner,
) : StringDeserializer() {

    @Throws(JacksonException::class)
    override fun deserialize(parser: JsonParser, context: DeserializationContext): String? {
        val value = super.deserialize(parser, context) ?: return null
        val requestUri = ServletUtils.getRequest()?.requestURI
        if (requestUri != null && properties.excludeUrls.any { pathMatcher.match(it, requestUri) }) {
            return value
        }
        return xssCleaner.clean(value)
    }
}
