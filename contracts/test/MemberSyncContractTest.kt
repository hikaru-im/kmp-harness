package im.hikaru.contracts.app.member

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemberSyncContractTest {
    @Test
    fun resourcesAndOperationsAreStableAndUnambiguous() {
        val resources = listOf(
            MemberAddressSyncContract.RESOURCE,
            MemberProfileSyncContract.RESOURCE,
        )

        assertEquals(resources.size, resources.toSet().size)
        assertTrue(resources.all { it.matches(Regex("[a-z][a-z0-9-]*")) })
        assertEquals(setOf("create", "update", "delete"), MemberAddressSyncContract.OPERATIONS)
        assertEquals(setOf("update"), MemberProfileSyncContract.OPERATIONS)
    }
}
