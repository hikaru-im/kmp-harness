package im.hikaru.ruoyi.module.sync.service

import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchRespVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangesQuery
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangesRespVO

interface SyncCommandService {
    fun submit(userId: Long, request: AppSyncCommandBatchReqVO): AppSyncCommandBatchRespVO

    fun getChanges(userId: Long, query: AppSyncChangesQuery): AppSyncChangesRespVO
}
