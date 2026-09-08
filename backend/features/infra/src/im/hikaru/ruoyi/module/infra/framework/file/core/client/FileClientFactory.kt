package im.hikaru.ruoyi.module.infra.framework.file.core.client

interface FileClientFactory {
    fun getFileClient(configId: Long): FileClient?

    fun createOrUpdateFileClient(configId: Long, storage: Int, config: FileClientConfig)
}
