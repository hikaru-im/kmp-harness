package im.hikaru.ruoyi.framework.mybatis.core.handler

import im.hikaru.ruoyi.framework.mybatis.core.dataobject.BaseEntity
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Fills common audit fields before Exposed insert and update operations. */
object DefaultDBFieldHandler {

    fun fillOnInsert(entity: BaseEntity, userId: Long? = currentUserId()) {
        val current = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        if (entity.createTime == null) entity.createTime = current
        if (entity.updateTime == null) entity.updateTime = current
        if (userId != null && entity.creator == null) entity.creator = userId.toString()
        if (userId != null && entity.updater == null) entity.updater = userId.toString()
    }

    fun fillOnUpdate(entity: BaseEntity, userId: Long? = currentUserId()) {
        if (entity.updateTime == null) {
            entity.updateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        }
        if (userId != null && entity.updater == null) entity.updater = userId.toString()
    }

    private fun currentUserId(): Long? = SecurityFrameworkUtils.getLoginUserId()
}
