package im.hikaru.ruoyi.module.infra.framework.file.core.client.s3

import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.hibernate.validator.constraints.URL

data class S3FileClientConfig(
    @field:NotBlank(message = "endpoint must not be blank")
    var endpoint: String = "",
    @field:URL(message = "domain must be a URL")
    var domain: String? = null,
    @field:NotBlank(message = "bucket must not be blank")
    var bucket: String = "",
    @field:NotBlank(message = "accessKey must not be blank")
    var accessKey: String = "",
    @field:NotBlank(message = "accessSecret must not be blank")
    var accessSecret: String = "",
    @field:NotNull(message = "enablePathStyleAccess must not be null")
    var enablePathStyleAccess: Boolean? = null,
    @field:NotNull(message = "enablePublicAccess must not be null")
    var enablePublicAccess: Boolean? = null,
    var region: String? = null,
) : FileClientConfig {
    @get:AssertTrue(message = "domain must not be blank for Qiniu storage")
    @get:JsonIgnore
    val domainValid: Boolean
        get() = !endpoint.contains(ENDPOINT_QINIU) || !domain.isNullOrBlank()

    companion object {
        const val ENDPOINT_QINIU = "qiniucs.com"
        const val ENDPOINT_ALIYUN = "aliyuncs.com"
        const val ENDPOINT_TENCENT = "myqcloud.com"
    }
}
