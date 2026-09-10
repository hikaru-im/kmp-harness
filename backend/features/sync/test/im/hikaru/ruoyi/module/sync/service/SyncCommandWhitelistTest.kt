package im.hikaru.ruoyi.module.sync.service

import im.hikaru.contracts.app.member.MemberAddressSyncContract
import im.hikaru.contracts.app.member.MemberProfileSyncContract
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SyncCommandWhitelistTest {

    private val whitelist = SyncCommandWhitelist()

    @Test
    fun `地址管理操作在白名单中`() {
        assertTrue(whitelist.isAllowed(MemberAddressSyncContract.RESOURCE, MemberAddressSyncContract.CREATE))
        assertTrue(whitelist.isAllowed(MemberAddressSyncContract.RESOURCE, MemberAddressSyncContract.UPDATE))
        assertTrue(whitelist.isAllowed(MemberAddressSyncContract.RESOURCE, MemberAddressSyncContract.DELETE))
    }

    @Test
    fun `个人资料更新在白名单中`() {
        assertTrue(whitelist.isAllowed(MemberProfileSyncContract.RESOURCE, MemberProfileSyncContract.UPDATE))
    }

    @Test
    fun `签到操作不在白名单中`() {
        assertFalse(
            whitelist.isAllowed("member-sign-in", "create"),
            "签到需要实时验证，不应该允许离线同步"
        )
    }

    @Test
    fun `未定义的操作不在白名单中`() {
        assertFalse(whitelist.isAllowed("unknown-resource", "unknown-operation"))
        assertFalse(whitelist.isAllowed("member-address", "unknown-operation"))
    }

    @Test
    fun `getAllowedOperations 返回完整白名单`() {
        val allowed = whitelist.getAllowedOperations()

        // 至少包含地址和资料
        assertTrue(allowed.contains(MemberAddressSyncContract.RESOURCE to MemberAddressSyncContract.CREATE))
        assertTrue(allowed.contains(MemberProfileSyncContract.RESOURCE to MemberProfileSyncContract.UPDATE))

        // 不包含签到
        assertFalse(allowed.contains("member-sign-in" to "create"))
    }
}
