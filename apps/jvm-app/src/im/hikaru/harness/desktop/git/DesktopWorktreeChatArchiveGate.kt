package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.ManagedWorktreeOwner
import im.hikaru.harness.session.SessionArchiveGate
import im.hikaru.harness.session.SessionHeader

/**
 * Durable-entry fence for Worktree-associated chats.
 *
 * A complete-header identity recorded in the archive store cannot be re-admitted as a live Session.
 * Reads fail closed: an unreadable archive document rejects the durable load instead of silently
 * re-admitting an archived chat. This is a same-Host, same-file boundary and not an absence or
 * deletion proof.
 */
public class DesktopWorktreeChatArchiveGate(
    private val store: DesktopWorktreeChatArchiveStore,
    private val owner: ManagedWorktreeOwner,
) : SessionArchiveGate {
    override val supportsManagement: Boolean = true
    override suspend fun setArchived(header: SessionHeader, archived: Boolean): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val identity = DesktopWorktreeSessionObserver.pinKey(header)
        val key = im.hikaru.harness.client.git.ChatArchiveKey(identity.id, identity.headerFingerprint)
        val old = store.read(owner)
        val keys = old.snapshot.archived
        if ((key in keys) != archived) {
            store.compareAndSet(old, old.snapshot.copy(revision = old.snapshot.revision + 1,
                archived = if (archived) keys + key else keys - key))
                .durabilityWarning
        } else null
    }
    override suspend fun isArchived(header: SessionHeader): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val fingerprint = DesktopWorktreeSessionObserver.pinKey(header).headerFingerprint
        store.read(owner).snapshot.archived.any {
            it.id == header.id.value && it.headerFingerprint == fingerprint
        }
    }
}

