package im.hikaru.ruoyi.module.infra.framework.file.core.client.local

import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.URL

data class LocalFileClientConfig(
    @field:NotBlank(message = "basePath must not be blank")
    var basePath: String = "",
    @field:NotBlank(message = "domain must not be blank")
    @field:URL(message = "domain must be a URL")
    var domain: String = "",
) : FileClientConfig
