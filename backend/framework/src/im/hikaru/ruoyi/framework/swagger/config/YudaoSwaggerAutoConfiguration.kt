package im.hikaru.ruoyi.framework.swagger.config

import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiBuilderCustomizer
import org.springdoc.core.customizers.ServerBaseUrlCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.providers.JavadocProvider
import org.springdoc.core.service.OpenAPIService
import org.springdoc.core.service.SecurityService
import org.springdoc.core.utils.PropertyResolverUtils
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import java.util.Optional

@AutoConfiguration(before = [Knife4jAutoConfiguration::class])
@ConditionalOnClass(OpenAPI::class)
@EnableConfigurationProperties(SwaggerProperties::class)
@ConditionalOnProperty(
    prefix = "springdoc.api-docs",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
@Import(Knife4jOpenApiCustomizer::class)
class YudaoSwaggerAutoConfiguration {

    @Bean
    fun createApi(properties: SwaggerProperties): OpenAPI = OpenAPI()
        .info(
            Info()
                .title(properties.title)
                .description(properties.description)
                .version(properties.version)
                .contact(
                    Contact()
                        .name(properties.author)
                        .url(properties.url)
                        .email(properties.email),
                )
                .license(License().name(properties.license).url(properties.licenseUrl)),
        )
        .components(
            Components().addSecuritySchemes(
                HttpHeaders.AUTHORIZATION,
                SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .name(HttpHeaders.AUTHORIZATION)
                    .`in`(SecurityScheme.In.HEADER),
            ),
        )
        .addSecurityItem(SecurityRequirement().addList(HttpHeaders.AUTHORIZATION))

    @Bean
    @Primary
    fun openApiBuilder(
        openApi: Optional<OpenAPI>,
        securityService: SecurityService,
        springDocConfigProperties: SpringDocConfigProperties,
        propertyResolverUtils: PropertyResolverUtils,
        openApiBuilderCustomizers: Optional<List<OpenApiBuilderCustomizer>>,
        serverBaseUrlCustomizers: Optional<List<ServerBaseUrlCustomizer>>,
        javadocProvider: Optional<JavadocProvider>,
    ): OpenAPIService = OpenAPIService(
        openApi,
        securityService,
        springDocConfigProperties,
        propertyResolverUtils,
        openApiBuilderCustomizers,
        serverBaseUrlCustomizers,
        javadocProvider,
    )

    @Bean
    fun allGroupedOpenApi(): GroupedOpenApi = buildGroupedOpenApi("all", "")

    companion object {
        @JvmStatic
        fun buildGroupedOpenApi(group: String): GroupedOpenApi = buildGroupedOpenApi(group, group)

        @JvmStatic
        fun buildGroupedOpenApi(group: String, path: String): GroupedOpenApi {
            val normalizedPath = path.trim('/')
            val adminPath = if (normalizedPath.isEmpty()) "/admin-api/**" else "/admin-api/$normalizedPath/**"
            val appPath = if (normalizedPath.isEmpty()) "/app-api/**" else "/app-api/$normalizedPath/**"
            return GroupedOpenApi.builder()
                .group(group)
                .pathsToMatch(adminPath, appPath)
                .addOperationCustomizer { operation, handlerMethod ->
                    operation
                        .addParametersItem(buildTenantHeaderParameter())
                        .addParametersItem(buildSecurityHeaderParameter())
                        .operationId(
                            "${handlerMethod.beanType.simpleName.removeSuffix("Controller")}_${handlerMethod.method.name}",
                        )
                }
                .build()
        }

        private fun buildTenantHeaderParameter(): Parameter = Parameter()
            .name(WebFrameworkUtils.HEADER_TENANT_ID)
            .description("租户编号")
            .`in`(SecurityScheme.In.HEADER.toString())
            .schema(
                IntegerSchema()
                    ._default(1)
                    .name(WebFrameworkUtils.HEADER_TENANT_ID)
                    .description("租户编号"),
            )

        private fun buildSecurityHeaderParameter(): Parameter = Parameter()
            .name(HttpHeaders.AUTHORIZATION)
            .description("认证 Token")
            .`in`(SecurityScheme.In.HEADER.toString())
            .schema(
                StringSchema()
                    ._default("Bearer test1")
                    .name(HttpHeaders.AUTHORIZATION)
                    .description("认证 Token"),
            )
    }
}
