package im.hikaru.ruoyi.module.infra.service.file

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config.FileConfigSaveReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileConfigDO
import im.hikaru.ruoyi.module.infra.dal.mysql.file.FileConfigDao
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_DELETE_FAIL_MASTER
import im.hikaru.ruoyi.module.infra.enums.ErrorCodeConstants.FILE_CONFIG_NOT_EXISTS
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientFactory
import im.hikaru.ruoyi.module.infra.framework.file.core.enums.FileStorageEnum
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

@Service
@Validated
class FileConfigServiceImpl(
    private val fileClientFactory: FileClientFactory,
    private val validator: Validator,
) : FileConfigService {

    private val clientCache: Cache<Long, FileClient> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofSeconds(10))
        .build()
    private val cacheLock = Any()

    override fun createFileConfig(createReqVO: FileConfigSaveReqVO): Long {
        val storage = requireNotNull(createReqVO.storage)
        return FileConfigDao.insert(FileConfigDO().apply {
            name = createReqVO.name
            this.storage = storage
            config = parseClientConfig(storage, requireNotNull(createReqVO.config))
            master = false
            remark = createReqVO.remark
        })
    }

    override fun updateFileConfig(updateReqVO: FileConfigSaveReqVO) {
        val id = requireNotNull(updateReqVO.id)
        val oldConfig = validateFileConfigExists(id)
        val storage = updateReqVO.storage ?: requireNotNull(oldConfig.storage)
        FileConfigDao.updateById(FileConfigDO().apply {
            this.id = id
            name = updateReqVO.name
            this.storage = storage
            config = parseClientConfig(storage, requireNotNull(updateReqVO.config))
            remark = updateReqVO.remark
        })
        clearCache(id, oldConfig.master == true)
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun updateFileConfigMaster(id: Long) {
        validateFileConfigExists(id)
        FileConfigDao.updateMaster(id)
        clientCache.invalidate(MASTER_CACHE_KEY)
        clientCache.invalidate(id)
    }

    override fun deleteFileConfig(id: Long) {
        val config = validateFileConfigExists(id)
        if (config.master == true) throw exception(FILE_CONFIG_DELETE_FAIL_MASTER)
        FileConfigDao.deleteById(id)
        clearCache(id, false)
    }

    override fun deleteFileConfigList(ids: List<Long>) {
        if (FileConfigDao.selectByIds(ids).any { it.master == true }) {
            throw exception(FILE_CONFIG_DELETE_FAIL_MASTER)
        }
        FileConfigDao.deleteByIds(ids)
        ids.forEach { clearCache(it, false) }
    }

    override fun getFileConfig(id: Long): FileConfigDO? = FileConfigDao.selectById(id)

    override fun getFileConfigPage(pageVO: FileConfigPageReqVO): PageResult<FileConfigDO> =
        FileConfigDao.selectPage(pageVO)

    override fun testFileConfig(id: Long): String {
        validateFileConfigExists(id)
        val client = requireNotNull(getFileClient(id)) { "File client $id does not exist" }
        return client.upload(
            "yudao file client test".toByteArray(StandardCharsets.UTF_8),
            "public/${UUID.randomUUID()}.txt",
            "text/plain",
        )
    }

    override fun getFileClient(id: Long): FileClient? = getOrLoadClient(id, false)

    override fun getMasterFileClient(): FileClient? = getOrLoadClient(MASTER_CACHE_KEY, true)

    private fun getOrLoadClient(cacheKey: Long, master: Boolean): FileClient? {
        clientCache.getIfPresent(cacheKey)?.let { return it }
        synchronized(cacheLock) {
            clientCache.getIfPresent(cacheKey)?.let { return it }
            val fileConfig = (if (master) FileConfigDao.selectByMaster() else FileConfigDao.selectById(cacheKey))
                ?: return null
            val id = requireNotNull(fileConfig.id)
            fileClientFactory.createOrUpdateFileClient(
                id,
                requireNotNull(fileConfig.storage),
                requireNotNull(fileConfig.config),
            )
            return fileClientFactory.getFileClient(id)?.also { clientCache.put(cacheKey, it) }
        }
    }

    private fun parseClientConfig(storage: Int, rawConfig: Map<String, Any?>): FileClientConfig {
        val storageType = requireNotNull(FileStorageEnum.fromStorage(storage)) { "Unknown file storage type: $storage" }
        val configWithType = LinkedHashMap(rawConfig).apply { this["@class"] = storageType.configClass.name }
        val clientConfig = requireNotNull(
            JsonUtils.parseObject(JsonUtils.toJsonString(configWithType), FileClientConfig::class.java),
        )
        require(storageType.configClass.isInstance(clientConfig)) {
            "Storage $storage requires ${storageType.configClass.name}"
        }
        val violations = validator.validate(clientConfig)
        if (violations.isNotEmpty()) throw ConstraintViolationException(violations)
        return clientConfig
    }

    private fun clearCache(id: Long, master: Boolean) {
        clientCache.invalidate(id)
        if (master) clientCache.invalidate(MASTER_CACHE_KEY)
    }

    private fun validateFileConfigExists(id: Long): FileConfigDO =
        FileConfigDao.selectById(id) ?: throw exception(FILE_CONFIG_NOT_EXISTS)

    companion object {
        private const val MASTER_CACHE_KEY = 0L
    }
}
