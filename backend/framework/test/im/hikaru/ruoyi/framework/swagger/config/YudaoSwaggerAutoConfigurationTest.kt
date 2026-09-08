package im.hikaru.ruoyi.framework.swagger.config

import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import com.github.xiaoymin.knife4j.annotations.ApiSupport
import com.github.xiaoymin.knife4j.core.conf.ExtensionsConstants
import com.github.xiaoymin.knife4j.core.conf.GlobalConstants
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jProperties
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.tags.Tag as OpenApiTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.method.HandlerMethod

class YudaoSwaggerAutoConfigurationTest {
    @Test
    fun `global open api contains project metadata and authorization scheme`() {
        val properties = SwaggerProperties().apply {
            title = "Yudao"
            description = "API description"
            author = "Author"
            version = "1.0.0"
            url = "https://example.com"
            email = "author@example.com"
            license = "MIT"
            licenseUrl = "https://example.com/license"
        }

        val openApi = YudaoSwaggerAutoConfiguration().createApi(properties)

        assertEquals("Yudao", openApi.info.title)
        assertEquals("API description", openApi.info.description)
        assertEquals("Author", openApi.info.contact.name)
        assertEquals("MIT", openApi.info.license.name)
        val authorization = openApi.components.securitySchemes.getValue(HttpHeaders.AUTHORIZATION)
        assertEquals(HttpHeaders.AUTHORIZATION, authorization.name)
        assertTrue(openApi.security.any { it.containsKey(HttpHeaders.AUTHORIZATION) })
    }

    @Test
    fun `grouped api adds headers and stable controller operation id`() {
        val all = YudaoSwaggerAutoConfiguration.buildGroupedOpenApi("all", "")
        assertEquals(listOf("/admin-api/**", "/app-api/**"), all.pathsToMatch)

        val system = YudaoSwaggerAutoConfiguration.buildGroupedOpenApi("system")
        assertEquals(listOf("/admin-api/system/**", "/app-api/system/**"), system.pathsToMatch)
        val handlerMethod = HandlerMethod(
            SampleController(),
            SampleController::class.java.getDeclaredMethod("listUsers"),
        )
        val operation = system.operationCustomizers.single().customize(Operation(), handlerMethod)

        assertEquals("Sample_listUsers", operation.operationId)
        assertEquals(
            setOf(WebFrameworkUtils.HEADER_TENANT_ID, HttpHeaders.AUTHORIZATION),
            operation.parameters.mapTo(linkedSetOf()) { it.name },
        )
    }

    @Test
    fun `knife4j customizer adds settings and tag order extensions`() {
        val knife4jProperties = Knife4jProperties().apply { isEnable = true }
        val springDocProperties = SpringDocConfigProperties().apply {
            addGroupConfig(
                SpringDocConfigProperties.GroupConfig().apply {
                    group = "test"
                    packagesToScan = listOf("im.hikaru.ruoyi.framework.swagger.config")
                },
            )
        }
        val openApi = OpenAPI().tags(listOf(OpenApiTag().name("Ordered API")))

        Knife4jOpenApiCustomizer(knife4jProperties, springDocProperties).customise(openApi)

        assertNotNull(openApi.extensions[GlobalConstants.EXTENSION_OPEN_API_NAME])
        assertEquals(
            42,
            openApi.tags.single().extensions[ExtensionsConstants.EXTENSION_ORDER],
        )
    }

    private class SampleController {
        fun listUsers() = Unit
    }

    @RestController
    @Tag(name = "Ordered API")
    @ApiSupport(order = 42)
    class OrderedController
}
