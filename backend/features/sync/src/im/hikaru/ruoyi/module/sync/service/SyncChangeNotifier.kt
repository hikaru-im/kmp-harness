package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.sync.SYNC_CHANGE_NOTIFICATION_TYPE
import im.hikaru.contracts.sync.SyncChangeNotification
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import im.hikaru.ruoyi.framework.websocket.core.sender.WebSocketMessageSender
import im.hikaru.ruoyi.module.sync.dal.mysql.AppSyncPushDeviceDao
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.stereotype.Component

fun interface SyncChangeNotifier {
    fun notifyChange(context: SyncCommandContext, notification: SyncChangeNotification)
}

@Component
class WebSocketSyncChangeNotifier(
    private val senderProvider: ObjectProvider<WebSocketMessageSender>,
) : SyncChangeNotifier {
    override fun notifyChange(context: SyncCommandContext, notification: SyncChangeNotification) {
        val sender = senderProvider.ifAvailable ?: return
        TenantUtils.execute(context.tenantId, Runnable {
            sender.send(
                UserTypeEnum.MEMBER.value,
                context.userId,
                SYNC_CHANGE_NOTIFICATION_TYPE,
                JsonUtils.toJsonString(notification),
            )
        })
    }
}

@Component
class SystemPushSyncChangeNotifier(
    private val gatewayProvider: ObjectProvider<SyncPushGateway>,
    @param:Qualifier("syncPushTaskExecutor")
    private val taskExecutorProvider: ObjectProvider<TaskExecutor>,
) : SyncChangeNotifier {
    override fun notifyChange(context: SyncCommandContext, notification: SyncChangeNotification) {
        val gateway = gatewayProvider.ifAvailable ?: return
        val task = Runnable {
            runCatching {
                val tokens = AppSyncPushDeviceDao.selectTokens(context)
                if (tokens.isEmpty()) return@Runnable
                val result = gateway.send(tokens, notification)
                AppSyncPushDeviceDao.deleteTokens(context, result.invalidTokens)
            }.onFailure { failure ->
                log.warn(
                    "Failed to send sync push for tenant={}, user={}, resource={}, cursor={}",
                    context.tenantId,
                    context.userId,
                    notification.resource,
                    notification.cursor,
                    failure,
                )
            }
        }
        taskExecutorProvider.ifAvailable?.execute(task) ?: task.run()
    }

    private companion object {
        val log = LoggerFactory.getLogger(SystemPushSyncChangeNotifier::class.java)
    }
}
