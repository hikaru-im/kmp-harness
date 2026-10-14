package im.hikaru.harness.session

import im.hikaru.harness.runtime.service.ServiceKey

/**
 * Optional durable archive identities for the current Host.
 *
 * When no gate is provided the store behaves exactly as before. A gate that reports a complete
 * header identity as archived fences the low-level Session entry points, so an archived chat
 * cannot be re-admitted and cannot produce new durable events. It is not an absence proof and it
 * does not by itself delete anything.
 */
public interface SessionArchiveGate {
    /** true when this exact complete-header identity is currently archived. */
    public suspend fun isArchived(header: SessionHeader): Boolean
}

public object SessionArchiveKey : ServiceKey<SessionArchiveGate>("session-archive")

