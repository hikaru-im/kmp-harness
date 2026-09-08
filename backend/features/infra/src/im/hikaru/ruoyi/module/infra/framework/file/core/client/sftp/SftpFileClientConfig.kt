package im.hikaru.ruoyi.module.infra.framework.file.core.client.sftp

import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.hibernate.validator.constraints.URL

data class SftpFileClientConfig(
    @field:NotBlank(message = "basePath must not be blank")
    var basePath: String = "",
    @field:NotBlank(message = "domain must not be blank")
    @field:URL(message = "domain must be a URL")
    var domain: String = "",
    @field:NotBlank(message = "host must not be blank")
    var host: String = "",
    @field:NotNull(message = "port must not be null")
    var port: Int? = null,
    @field:NotBlank(message = "username must not be blank")
    var username: String = "",
    @field:NotBlank(message = "password must not be blank")
    var password: String = "",
) : FileClientConfig
