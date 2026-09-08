package im.hikaru.ruoyi.module.infra.framework.file.core.client

import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import org.slf4j.LoggerFactory

abstract class AbstractFileClient<Config : FileClientConfig>(
    final override val id: Long,
    config: Config,
) : FileClient {

    @Volatile
    protected var config: Config = config
        private set

    private var originalConfig: Config = config

    @Synchronized
    fun init() {
        doInit()
        log.debug("[init][file client {} initialized]", id)
    }

    @Synchronized
    internal fun refresh(newConfig: FileClientConfig) {
        require(config::class == newConfig::class) {
            "File client $id cannot change config type from ${config::class.qualifiedName} to ${newConfig::class.qualifiedName}"
        }
        @Suppress("UNCHECKED_CAST")
        val typedConfig = newConfig as Config
        if (typedConfig == originalConfig) return
        config = typedConfig
        originalConfig = typedConfig
        init()
    }

    protected abstract fun doInit()

    protected fun formatFileUrl(domain: String, path: String): String =
        "${domain.trimEnd('/')}/admin-api/infra/file/$id/get/${HttpUtils.encodeUrlPath(path)}"

    companion object {
        private val log = LoggerFactory.getLogger(AbstractFileClient::class.java)
    }
}
