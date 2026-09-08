package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.sync.SyncPushPlatform

interface SyncPushDeviceService {
    fun register(userId: Long, token: String, platform: SyncPushPlatform)

    fun unregister(userId: Long, token: String)
}
