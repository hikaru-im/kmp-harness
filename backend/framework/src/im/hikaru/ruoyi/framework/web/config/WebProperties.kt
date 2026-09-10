package im.hikaru.ruoyi.framework.web.config

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Web 配置项 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
@ConfigurationProperties(prefix = "yudao.web")
@Validated
class WebProperties {

    @field:NotNull(message = "APP API 不能为空")
    @field:Valid
    var appApi: Api = Api("/app-api", "**.controller.app.**")

    @field:NotNull(message = "Admin API 不能为空")
    @field:Valid
    var adminApi: Api = Api("/admin-api", "**.controller.admin.**")

    @field:NotNull(message = "Admin UI 不能为空")
    @field:Valid
    var adminUi: Ui? = null

    /** API 配置 */
    class Api {
        /** API 前缀 */
        @field:NotEmpty(message = "API 前缀不能为空")
        var prefix: String

        /** Controller 所在包的 Ant 路径规则 */
        @field:NotEmpty(message = "Controller 所在包不能为空")
        var controller: String

        constructor(prefix: String, controller: String) {
            this.prefix = prefix
            this.controller = controller
        }

        @Suppress("unused")
        constructor() {
            this.prefix = ""
            this.controller = ""
        }
    }

    /** UI 配置 */
    class Ui {
        /** 访问地址 */
        var url: String? = null
    }
}
