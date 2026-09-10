package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.app.member.MemberAddressSyncContract
import im.hikaru.contracts.app.member.MemberProfileSyncContract
import org.springframework.stereotype.Component

/**
 * 同步命令白名单 — 只有显式列入的操作才允许离线排队和延迟同步。
 *
 * **设计原则：**
 * - ✅ 个人数据管理（地址、资料、收藏、笔记）可以离线优先
 * - ✅ 容忍 7 天内同步的操作
 * - ✅ 冲突可通过 KeepLocal/UseServer 解决
 *
 * **禁止列入白名单：**
 * - ❌ 防作弊/防重放（签到、抽奖、投票、兑换）
 * - ❌ 涉及库存/配额（积分扣减、限量商品）
 * - ❌ 安全敏感（密码、验证码、社交授权）
 * - ❌ 外部系统（支付、短信、文件上传）
 * - ❌ 需要实时验证（时间窗口、GPS位置）
 */
@Component
class SyncCommandWhitelist {

    private val allowedOperations: Set<Pair<String, String>> = setOf(
        // ✅ 地址管理 - 个人数据，容忍延迟，冲突可解决
        MemberAddressSyncContract.RESOURCE to MemberAddressSyncContract.CREATE,
        MemberAddressSyncContract.RESOURCE to MemberAddressSyncContract.UPDATE,
        MemberAddressSyncContract.RESOURCE to MemberAddressSyncContract.DELETE,

        // ✅ 个人资料 - 昵称、头像等个人数据
        MemberProfileSyncContract.RESOURCE to MemberProfileSyncContract.UPDATE,
    )

    /**
     * 检查指定操作是否允许同步
     */
    fun isAllowed(aggregateType: String, operation: String): Boolean {
        return (aggregateType to operation) in allowedOperations
    }

    /**
     * 获取所有白名单操作（用于文档生成或测试）
     */
    fun getAllowedOperations(): Set<Pair<String, String>> {
        return allowedOperations
    }
}
