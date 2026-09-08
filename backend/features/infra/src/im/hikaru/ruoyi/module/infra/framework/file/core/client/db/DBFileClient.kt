package im.hikaru.ruoyi.module.infra.framework.file.core.client.db

import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileContentDO
import im.hikaru.ruoyi.module.infra.dal.mysql.file.FileContentDao
import im.hikaru.ruoyi.module.infra.framework.file.core.client.AbstractFileClient
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FilePathUtils

class DBFileClient(id: Long, config: DBFileClientConfig) : AbstractFileClient<DBFileClientConfig>(id, config) {
    override fun doInit() = Unit

    override fun upload(content: ByteArray, path: String, type: String): String {
        FilePathUtils.validatePath(path)
        FileContentDao.insert(FileContentDO().apply {
            configId = id
            this.path = path
            this.content = content
        })
        return formatFileUrl(config.domain, path)
    }

    override fun delete(path: String) {
        FilePathUtils.validatePath(path)
        FileContentDao.deleteByConfigIdAndPath(id, path)
    }

    override fun getContent(path: String): ByteArray? {
        FilePathUtils.validatePath(path)
        return FileContentDao.selectLatestByConfigIdAndPath(id, path)?.content
    }
}
