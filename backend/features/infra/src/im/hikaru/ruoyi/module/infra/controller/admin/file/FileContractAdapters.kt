package im.hikaru.ruoyi.module.infra.controller.admin.file

import im.hikaru.contracts.infra.FileInfo
import im.hikaru.contracts.infra.FilePresignedUrl
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FilePresignedUrlRespVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.file.FileRespVO

internal fun FilePresignedUrlRespVO.toContract() = FilePresignedUrl(
    configId = configId,
    uploadUrl = uploadUrl,
    url = url,
    path = path,
)

internal fun FileRespVO.toContract() = FileInfo(
    id = id,
    configId = configId,
    path = path,
    name = name,
    url = url,
    type = type,
    size = size,
    createTime = createTime?.toString(),
)
