package im.hikaru.ruoyi.module.infra.framework.file.core.client.db

import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig
import jakarta.validation.constraints.NotBlank
import org.hibernate.validator.constraints.URL

data class DBFileClientConfig(
    @field:NotBlank(message = "domain must not be blank")
    @field:URL(message = "domain must be a URL")
    var domain: String = "",
) : FileClientConfig
