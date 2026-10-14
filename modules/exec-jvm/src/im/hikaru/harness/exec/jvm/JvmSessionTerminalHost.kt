package im.hikaru.harness.exec.jvm

import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentKey
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.api.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns a bounded set of actual Session bindings, including unconfirmed retired resources. */
public class JvmSessionTerminalHost(
    private val context: Context,
    private val maxBindings: Int = 16,
    private val limits: JvmPtyTerminalSessions.Limits = JvmPtyTerminalSessions.Limits(),
    allowedRoots: List<Path> = emptyList(),
) : SessionTerminalHostService, Disposable {
    init { require(maxBindings > 0 && allowedRoots.all { it.isAbsolute }) }

    private val root = context.root
    private val store = context.require(SessionKey)
    private val agents = context.require(AgentKey)
    private val allowedRoots = allowedRoots.toList()
    private val scope = TerminalScope(UUID.randomUUID().toString())
    private val gate = Mutex()
    private val stopped = AtomicBoolean(false)
    private val bindings = linkedMapOf<TerminalOwner, Binding>()
    private val supported = System.getProperty("os.name") == "Mac OS X" &&
        System.getProperty("os.arch") in setOf("x86_64", "amd64")

    private class Binding(val agent: Agent, val session: Session, val cwd: Path, val terminals: JvmPtyTerminalSessions) {
        var retired = false
    }

    /** Diagnostics and a gate after real native delivery; neither substitutes a native operation. */
    internal var afterNativeWrite: ((TerminalKey) -> Unit)? = null
    internal suspend fun diagnostics(key: TerminalKey): JvmPtyTerminalSessions.Diagnostics? =
        gate.withLock { bindings[key.owner]?.terminals?.diagnostics(key) }
    internal suspend fun bindingCount(): Int = gate.withLock { bindings.size }

    override suspend fun discover(session: SessionId): HostTerminalDiscoveryResult = withContext(Dispatchers.IO) {
        gate.withLock {
            if (stopped.get()) return@withLock HostTerminalDiscoveryResult.Unavailable(session, "Terminal Host is stopped")
            if (!supported) return@withLock HostTerminalDiscoveryResult.Unavailable(session, "Unsupported PTY platform")
            reap()
            val agent = liveAgent(session)
                ?: return@withLock HostTerminalDiscoveryResult.Rejected(session, "Session has no exact live Host owner")
            val existing = bindings.values.firstOrNull { !it.retired && it.agent === agent && it.session === agent.session }
            val binding = if (existing != null) existing else {
                if (bindings.size >= maxBindings) return@withLock HostTerminalDiscoveryResult.Rejected(session, "Terminal binding capacity exhausted; cleanup may be pending")
                val cwd = try { canonicalCwd(agent.session) } catch (_: Exception) {
                    return@withLock HostTerminalDiscoveryResult.Rejected(session, "Session requires a directory within the allowed Host roots")
                }
                if (!current(agent, agent.session)) return@withLock HostTerminalDiscoveryResult.Rejected(session, "Session changed during discovery")
                coroutineContext.ensureActive()
                if (stopped.get()) return@withLock HostTerminalDiscoveryResult.Unavailable(session, "Terminal Host is stopped")
                // Construction only allocates the bounded emulator service. No shell starts here.
                Binding(agent, agent.session, cwd, JvmPtyTerminalSessions(scope, session, cwd, limits)).also {
                    it.terminals.afterNativeWrite = { key -> afterNativeWrite?.invoke(key) }
                    bindings[it.terminals.owner] = it
                }
            }
            if (!current(binding)) {
                retire(binding)
                HostTerminalDiscoveryResult.Rejected(session, "Session changed before discovery publication")
            } else HostTerminalDiscoveryResult.Available(session, binding.terminals.owner, binding.cwd.toString(), limits.capacity)
        }
    }

    override suspend fun list(owner: TerminalOwner): TerminalListResult = withContext(Dispatchers.IO) {
        gate.withLock {
            if (stopped.get()) return@withLock TerminalListResult.Unavailable(owner, "Terminal Host is stopped")
            val binding = trusted(owner) ?: return@withLock TerminalListResult.Rejected(owner, "Stale terminal owner")
            val result = binding.terminals.list(owner)
            if (!current(binding) || result.owner != owner) {
                retire(binding)
                TerminalListResult.Rejected(owner, "Terminal owner changed before publication")
            } else when (result) {
                is TerminalListResult.Available -> result.copy(terminals = result.terminals.map { it.snapshotCopy() })
                else -> result
            }
        }
    }

    override suspend fun read(key: TerminalKey): TerminalReadResult = withContext(Dispatchers.IO) {
        gate.withLock {
            if (stopped.get()) return@withLock TerminalReadResult.Unavailable(key, "Terminal Host is stopped")
            val binding = trusted(key.owner) ?: return@withLock TerminalReadResult.Rejected(key, "Stale terminal owner")
            val result = binding.terminals.read(key)
            if (!current(binding) || result.key != key) {
                retire(binding)
                TerminalReadResult.Rejected(key, "Terminal owner changed before publication")
            } else when (result) {
                is TerminalReadResult.Available -> result.copy(snapshot = result.snapshot.snapshotCopy())
                else -> result
            }
        }
    }

    override suspend fun create(request: TerminalCreateRequest): HostTerminalCreateResult {
        val caller = coroutineContext[Job]
        val captured = request.copy(argv = request.argv.toList(), environment = request.environment.toMap())
        // Keep the caller's dispatcher for the return handoff. IO is nested under NonCancellable.
        return withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                gate.withLock {
                    if (caller?.isActive == false) return@withLock HostTerminalCreateResult.Rejected(captured.owner, "Caller cancelled before dispatch")
                    if (stopped.get()) return@withLock HostTerminalCreateResult.Unavailable(captured.owner, "Terminal Host is stopped")
                    val binding = trusted(captured.owner)
                        ?: return@withLock HostTerminalCreateResult.Rejected(captured.owner, "Stale terminal owner")
                    // Canonical request cwd is checked by the base service against the captured Session root.
                    if (caller?.isActive == false || !current(binding)) return@withLock HostTerminalCreateResult.Rejected(captured.owner, "Owner or caller changed before dispatch")
                    var knownKey: TerminalKey? = null
                    try {
                        val result = binding.terminals.create(captured)
                        knownKey = when (result) {
                            is TerminalCreateResult.Created -> result.metadata.key.takeIf { it.owner == captured.owner }
                            is TerminalCreateResult.UnknownOutcome -> result.key.takeIf { it.owner == captured.owner }
                            else -> null
                        }
                        val live = current(binding)
                        if (!live) retire(binding)
                        if (caller?.isActive == false || !live || result.owner != captured.owner) {
                            HostTerminalCreateResult.UnknownOutcome(captured.owner, knownKey, "Create was dispatched; acknowledgement is unknown")
                        } else when (result) {
                            is TerminalCreateResult.Created -> HostTerminalCreateResult.Created(captured.owner, result.metadata.snapshotCopy())
                            is TerminalCreateResult.UnknownOutcome -> HostTerminalCreateResult.UnknownOutcome(captured.owner, knownKey, result.reason)
                            is TerminalCreateResult.Rejected -> HostTerminalCreateResult.Rejected(captured.owner, result.reason)
                            is TerminalCreateResult.Unavailable -> HostTerminalCreateResult.Unavailable(captured.owner, result.reason)
                        }
                    } catch (_: Exception) {
                        HostTerminalCreateResult.UnknownOutcome(captured.owner, knownKey, "Create was dispatched; acknowledgement is unknown")
                    }
                }
            }
        }
    }

    override suspend fun write(key: TerminalKey, input: String): TerminalMutationResult = mutate(key) { it.write(key, input) }
    override suspend fun input(key: TerminalKey, input: TerminalInput): TerminalMutationResult {
        val captured = when (input) {
            is TerminalInput.Text -> input.copy()
            is TerminalInput.Paste -> input.copy()
            is TerminalInput.KeyPress -> TerminalInput.KeyPress(input.key, input.modifiers.toSet(), input.character)
        }
        return mutate(key) { it.input(key, captured) }
    }
    override suspend fun resize(key: TerminalKey, size: TerminalSize): TerminalMutationResult = mutate(key) { it.resize(key, size) }
    override suspend fun close(key: TerminalKey): TerminalMutationResult = mutate(key) { it.close(key) }

    private suspend fun mutate(key: TerminalKey, operation: suspend (JvmPtyTerminalSessions) -> TerminalMutationResult): TerminalMutationResult {
        val caller = coroutineContext[Job]
        return withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                gate.withLock {
                    if (caller?.isActive == false) return@withLock TerminalMutationResult.Rejected(key, "Caller cancelled before dispatch")
                    if (stopped.get()) return@withLock TerminalMutationResult.Unavailable(key, "Terminal Host is stopped")
                    val binding = trusted(key.owner) ?: return@withLock TerminalMutationResult.Rejected(key, "Stale terminal owner")
                    if (caller?.isActive == false || !current(binding)) return@withLock TerminalMutationResult.Rejected(key, "Owner or caller changed before dispatch")
                    try {
                        val result = operation(binding.terminals)
                        val live = current(binding)
                        if (!live) retire(binding)
                        if (caller?.isActive == false || !live || result.key != key) {
                            TerminalMutationResult.UnknownOutcome(key, "Mutation was dispatched; acknowledgement is unknown")
                        } else result
                    } catch (_: Exception) {
                        TerminalMutationResult.UnknownOutcome(key, "Mutation was dispatched; acknowledgement is unknown")
                    }
                }
            }
        }
    }

    private suspend fun liveAgent(session: SessionId): Agent? {
        if (stopped.get()) return null
        return try {
            agents.get(AgentId(session.value))?.takeIf { it.session.id == session && current(it, it.session) }
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
    }

    private suspend fun current(binding: Binding): Boolean = !binding.retired && current(binding.agent, binding.session)

    private suspend fun current(agent: Agent, session: Session): Boolean {
        if (stopped.get()) return false
        return try {
            context.root === root && !root.isDisposed && !context.isDisposed && !agent.context.isDisposed &&
                context.get(SessionKey) === store && context.get(AgentKey) === agents &&
                root.get(SessionKey) === store && root.get(AgentKey) === agents &&
                agent.context.root === root && agent.context.get(SessionKey) === store && agent.context.get(AgentKey) === agents &&
                agent.id.value == session.id.value && agent.session === session &&
                agents.get(agent.id) === agent && store.get(session.id) === session && session.isLive() &&
                agents.get(agent.id) === agent && !agent.context.isDisposed && !stopped.get()
        } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { false }
    }

    private fun canonicalCwd(session: Session): Path {
        val declared = Path.of(session.header.cwd ?: error("Missing Session cwd"))
        require(declared.isAbsolute)
        val canonical = declared.toRealPath()
        require(Files.isDirectory(canonical))
        require(allowedRoots.isEmpty() || allowedRoots.any { canonical.startsWith(it.toRealPath()) })
        return canonical
    }

    private suspend fun trusted(owner: TerminalOwner): Binding? {
        val binding = bindings[owner] ?: return null
        if (current(binding)) return binding
        retire(binding)
        return null
    }

    private suspend fun reap() {
        for (binding in bindings.values.toList()) if (binding.retired || !current(binding)) retire(binding)
    }

    /** Only a confirmed base shutdown removes ownership. Unknown stays counted and retryable for cleanup. */
    private suspend fun retire(binding: Binding) {
        binding.retired = true
        val result = withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                try { binding.terminals.shutdown(binding.terminals.owner) } catch (_: Exception) { null }
            }
        }
        if (result is TerminalShutdownResult.Applied && result.owner == binding.terminals.owner) {
            bindings.remove(binding.terminals.owner)
        }
    }

    override suspend fun dispose() {
        stopped.set(true) // fence starts before waiting for a dispatched mutation
        withContext(NonCancellable) {
            withContext(Dispatchers.IO) {
                gate.withLock {
                    bindings.values.toList().forEach { retire(it) }
                    check(bindings.isEmpty()) { "PTY Host cleanup unconfirmed for ${bindings.size} owned Session bindings" }
                }
            }
        }
    }
}

private fun TerminalMetadata.snapshotCopy(): TerminalMetadata = copy(argv = argv.toList())

private fun TerminalSnapshot.snapshotCopy(): TerminalSnapshot {
    fun lines(source: List<TerminalLineSnapshot>): List<TerminalLineSnapshot> = source.map { line ->
        line.copy(runs = line.runs.map { run -> run.copy(style = run.style.copy(attributes = run.style.attributes.toSet())) })
    }
    return copy(metadata = metadata.snapshotCopy(), screen = lines(screen), history = lines(history), cursor = cursor.copy())
}
