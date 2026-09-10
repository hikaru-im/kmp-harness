package im.hikaru.contracts.app.member

/** Stable wire identifiers shared by the App and the member sync handlers. */
object MemberAddressSyncContract {
    const val RESOURCE = "member-address"
    const val CREATE = "create"
    const val UPDATE = "update"
    const val DELETE = "delete"

    val OPERATIONS: Set<String> = setOf(CREATE, UPDATE, DELETE)
}

object MemberProfileSyncContract {
    const val RESOURCE = "member-profile"
    const val UPDATE = "update"

    val OPERATIONS: Set<String> = setOf(UPDATE)
}
