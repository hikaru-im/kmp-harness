package im.hikaru.ruoyi.framework.swagger.config

import com.github.xiaoymin.knife4j.annotations.ApiSupport
import com.github.xiaoymin.knife4j.core.conf.ExtensionsConstants
import com.github.xiaoymin.knife4j.core.conf.GlobalConstants
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties
import com.github.xiaoymin.knife4j.spring.extension.OpenApiExtensionResolver
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.models.OpenAPI
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.context.annotation.Primary
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.RestController

@Primary
class Knife4jOpenApiCustomizer(
    private val knife4jProperties: Knife4jProperties,
    private val springDocProperties: SpringDocConfigProperties,
) : com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer(
    knife4jProperties,
    springDocProperties,
), GlobalOpenApiCustomizer {

    override fun customise(openApi: OpenAPI) {
        if (!knife4jProperties.isEnable) return
        val resolver = OpenApiExtensionResolver(
            knife4jProperties.setting,
            knife4jProperties.documents,
        ).apply { start() }
        openApi.addExtension(
            GlobalConstants.EXTENSION_OPEN_API_NAME,
            mapOf(
                GlobalConstants.EXTENSION_OPEN_SETTING_NAME to knife4jProperties.setting,
                GlobalConstants.EXTENSION_OPEN_MARKDOWN_NAME to resolver.markdownFiles,
            ),
        )
        addTagOrderExtensions(openApi)
    }

    private fun addTagOrderExtensions(openApi: OpenAPI) {
        val packages = springDocProperties.groupConfigs
            .flatMapTo(linkedSetOf()) { it.packagesToScan.orEmpty() }
        if (packages.isEmpty()) return

        val tagOrders = packages.asSequence()
            .flatMap(::scanRestControllers)
            .mapNotNull { controller ->
                val apiSupport = controller.getAnnotation(ApiSupport::class.java) ?: return@mapNotNull null
                val tag = controller.getAnnotation(Tag::class.java)
                    ?: controller.interfaces.firstNotNullOfOrNull { it.getAnnotation(Tag::class.java) }
                    ?: return@mapNotNull null
                tag.name to apiSupport.order
            }
            .toMap()
        openApi.tags.orEmpty().forEach { tag ->
            tagOrders[tag.name]?.let { tag.addExtension(ExtensionsConstants.EXTENSION_ORDER, it) }
        }
    }

    private fun scanRestControllers(packageName: String): Sequence<Class<*>> {
        val scanner = ClassPathScanningCandidateComponentProvider(false).apply {
            addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        }
        return scanner.findCandidateComponents(packageName).asSequence().mapNotNull(::loadClass)
    }

    private fun loadClass(beanDefinition: BeanDefinition): Class<*>? =
        beanDefinition.beanClassName?.let { runCatching { Class.forName(it) }.getOrNull() }
}
