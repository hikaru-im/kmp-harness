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
    override suspend fun isArchived(header: SessionHeader): Boolean {
        val fingerprint = DesktopWorktreeSessionObserver.pinKey(header).headerFingerprint
        return store.read(owner).snapshot.archived.any {
            it.id == header.id.value && it.headerFingerprint == fingerprint
        }
    }
}

