package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.scheduling.ScheduledScope
import java.io.Closeable
import java.io.File
import java.net.JarURLConnection
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val LEASE_PROCESS_TIMEOUT_SECONDS = 30L
private const val LEASE_PROCESS_CLEANUP_SECONDS = 10L

/** OS-process lease evidence only; these tests do not exercise ScheduledExecutor crash recovery. */
class DesktopScheduledLeaderLeaseProcessTest {
    @Test
    fun contenderAcquiresOnlyAfterHolderReleasesAndExitsNormally() = withLeaseProcesses { fixture ->
        val holder = fixture.start("holder")
        holder.exchange("ACQUIRE", "ACQUIRE true true")
        fixture.assertStableLock()

        val contender = fixture.start("contender")
        assertNotEquals(holder.pid, contender.pid, "Contenders must be separate JVM processes")
        contender.exchange("ACQUIRE", "ACQUIRE false false")
        holder.exchange("HELD", "HELD true")
        fixture.assertStableLock()

        // Releasing a failed acquisition must not disturb the other process's lease.
        contender.exchange("RELEASE", "RELEASE false")
        contender.exchange("ACQUIRE", "ACQUIRE false false")
        holder.exchange("HELD", "HELD true")
        fixture.assertStableLock()

        holder.exchange("RELEASE", "RELEASE false")
        fixture.assertStableLock()
        holder.exitNormally()
        fixture.assertStableLock()

        // Reuse the same losing JVM and lease instance; do not recreate the lock file.
        contender.exchange("ACQUIRE", "ACQUIRE true true")
        contender.exchange("HELD", "HELD true")
        fixture.assertStableLock()
        contender.exchange("RELEASE", "RELEASE false")
        contender.exitNormally()
        fixture.assertStableLock()
    }

    @Test
    fun contenderAcquiresAfterHoldingJvmIsForciblyTerminated() = withLeaseProcesses { fixture ->
        val holder = fixture.start("holder")
        holder.exchange("ACQUIRE", "ACQUIRE true true")
        fixture.assertStableLock()

        val contender = fixture.start("contender")
        assertNotEquals(holder.pid, contender.pid, "Contenders must be separate JVM processes")
        contender.exchange("ACQUIRE", "ACQUIRE false false")
        holder.exchange("HELD", "HELD true")
        fixture.assertStableLock()

        // Only this fixture's child is killed, while it is waiting with the lease held.
        holder.exitForcibly()
        fixture.assertStableLock()
        contender.exchange("ACQUIRE", "ACQUIRE true true")
        contender.exchange("HELD", "HELD true")
        fixture.assertStableLock()
        contender.exchange("RELEASE", "RELEASE false")
        contender.exitNormally()
        fixture.assertStableLock()
    }

    private fun withLeaseProcesses(block: (LeaseProcesses) -> Unit) {
        val classpath = leaseProcessClasspath()
        val directory = Files.createTempDirectory("harness-leader-lease-process-")
        LeaseProcesses(directory, classpath).use { fixture ->
            fixture.createStableLock()
            block(fixture)
        }
    }
}

private class LeaseProcesses(private val directory: Path, private val classpath: String) : Closeable {
    private val lockPath = directory.resolve("leader.lock")
    private var originalFileKey: Any? = null
    private val processes = mutableListOf<Process>()
    private val children = mutableListOf<LeaseChild>()

    fun createStableLock() {
        Files.createFile(lockPath)
        originalFileKey = lockAttributes().fileKey()
        assertStableLock()
    }

    fun assertStableLock() {
        assertTrue(Files.exists(lockPath, NOFOLLOW_LINKS), "The stable leader.lock must remain present")
        val attributes = lockAttributes()
        assertTrue(attributes.isRegularFile, "leader.lock must remain a regular file")
        // A null fileKey is allowed by the filesystem API; contention still must pass.
        if (originalFileKey != null) {
            assertEquals(originalFileKey, attributes.fileKey(), "leader.lock must retain its file identity")
        }
    }

    private fun lockAttributes(): BasicFileAttributes =
        Files.readAttributes(lockPath, BasicFileAttributes::class.java, NOFOLLOW_LINKS)

    fun start(role: String): LeaseChild {
        val executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "java.exe"
        } else {
            "java"
        }
        val stderr = directory.resolve("$role.stderr")
        val process = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", executable).toString(),
            "-cp", classpath,
            DesktopScheduledLeaderLeaseProcessHelper::class.java.name,
            lockPath.toAbsolutePath().toString(), role,
        ).directory(directory.toFile()).redirectError(stderr.toFile()).start()
        // Register before reading READY: startup/classpath failures must also be cleaned up.
        processes += process
        val child = LeaseChild(process, stderr)
        children += child
        child.expect("READY ${child.pid}")
        assertNotEquals(ProcessHandle.current().pid(), child.pid, "The helper must run in a child JVM")
        assertTrue(process.isAlive, "The helper must stay alive after READY")
        return child
    }

    override fun close() {
        var failure: Throwable? = null
        fun clean(action: () -> Unit) {
            try {
                action()
            } catch (error: Throwable) {
                val first = failure
                if (first == null) failure = error else first.addSuppressed(error)
            }
        }

        // Issue termination to every child before waiting on any one of them.
        processes.forEach { process -> clean { if (process.isAlive) process.destroyForcibly() } }
        processes.forEach { process ->
            clean {
                check(process.waitFor(LEASE_PROCESS_CLEANUP_SECONDS, TimeUnit.SECONDS)) {
                    "Could not terminate test child JVM ${process.pid()}"
                }
            }
        }
        children.forEach { child -> clean { child.close() } }
        // No lock inode is removed until all processes which could hold it have exited.
        if (processes.none { it.isAlive }) {
            clean {
                Files.walk(directory).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { path -> clean { Files.delete(path) } }
                }
            }
        } else {
            clean { error("Test child JVM still alive; preserving its lock directory: $directory") }
        }
        failure?.let { throw it }
    }
}

private data class LeaseChildOutput(val line: String? = null, val error: Throwable? = null)

private class LeaseChild(private val process: Process, private val stderr: Path) : Closeable {
    val pid: Long = process.pid()
    private val output = LinkedBlockingQueue<LeaseChildOutput>()
    private val transcript = StringBuffer()
    private val writer = process.outputStream.bufferedWriter(Charsets.UTF_8)
    private val io = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "leader-lease-test-io-$pid").apply { isDaemon = true }
    }

    init {
        io.submit {
            try {
                process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        transcript.append(line).append('\n')
                        output.offer(LeaseChildOutput(line = line))
                    }
                }
            } catch (error: Exception) {
                output.offer(LeaseChildOutput(error = error))
            } finally {
                output.offer(LeaseChildOutput()) // EOF wakes the parent immediately on a crash.
            }
        }
    }

    fun exchange(command: String, expected: String) {
        val sent = io.submit {
            writer.write(command)
            writer.newLine()
            writer.flush()
        }
        try {
            sent.get(LEASE_PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (error: Exception) {
            sent.cancel(true)
            throw AssertionError("Failed to send $command to child $pid\n${diagnostics()}", error)
        }
        expect(expected)
    }

    fun expect(expected: String) {
        val received = output.poll(LEASE_PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            ?: throw AssertionError("Timed out waiting for '$expected' from child $pid\n${diagnostics()}")
        received.error?.let { throw AssertionError("Child $pid stdout failed\n${diagnostics()}", it) }
        assertEquals(expected, received.line, "Unexpected reply/EOF from child $pid\n${diagnostics()}")
    }

    fun exitNormally() {
        exchange("EXIT", "BYE")
        awaitExit()
        assertEquals(0, process.exitValue(), "Child $pid must exit normally\n${diagnostics()}")
    }

    fun exitForcibly() {
        assertTrue(process.isAlive, "The holding child $pid must be alive before forced termination")
        process.destroyForcibly()
        awaitExit()
        assertNotEquals(0, process.exitValue(), "Forced termination must not look like a normal exit")
    }

    private fun awaitExit() {
        assertTrue(
            process.waitFor(LEASE_PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            "Child $pid did not exit within the timeout\n${diagnostics()}",
        )
        assertFalse(process.isAlive, "Child $pid must be gone before takeover")
    }

    private fun diagnostics(): String {
        val errors = if (Files.exists(stderr)) Files.readString(stderr) else "<no stderr file>"
        return "alive=${process.isAlive}\nstdout:\n$transcript\nstderr:\n$errors"
    }

    override fun close() {
        io.shutdownNow()
        try {
            writer.close()
        } finally {
            try {
                process.inputStream.close()
                process.errorStream.close()
            } finally {
                check(io.awaitTermination(LEASE_PROCESS_CLEANUP_SECONDS, TimeUnit.SECONDS)) {
                    "I/O workers did not stop for test child JVM $pid"
                }
            }
        }
    }
}

private fun leaseProcessClasspath(): String {
    val entries = linkedSetOf<Path>()
    fun addUrl(url: URL) {
        when (url.protocol) {
            "file" -> entries.add(Path.of(url.toURI()).toAbsolutePath().normalize())
            "jar" -> addUrl((url.openConnection() as JarURLConnection).jarFileURL)
            else -> error("Cannot use $url as a child JVM classpath entry")
        }
    }

    // ConsoleLauncher/Amper can load tests outside java.class.path. Put their actual
    // code sources first so the launcher JAR cannot shadow the test runtime's Kotlin.
    val requiredClasses = listOf(
        DesktopScheduledLeaderLeaseProcessHelper::class.java,
        DesktopScheduledLeaderLease::class.java,
        ScheduledScope::class.java,
        HostId::class.java,
        Unit::class.java,
        Dispatchers::class.java,
        kotlinx.serialization.Serializable::class.java,
    )
    requiredClasses.forEach { type ->
        val location = type.protectionDomain?.codeSource?.location
        if (location != null) {
            addUrl(location)
        } else {
            val resourceName = type.name.replace('.', '/') + ".class"
            val resource = requireNotNull(type.getResource("/$resourceName")) {
                "No runtime location for ${type.name}"
            }
            if (resource.protocol == "file") {
                var root = Path.of(resource.toURI())
                repeat(resourceName.split('/').size) { root = root.parent }
                entries.add(root.toAbsolutePath().normalize())
            } else {
                addUrl(resource)
            }
        }
    }
    val visited = mutableSetOf<ClassLoader>()
    (requiredClasses.map { it.classLoader } + Thread.currentThread().contextClassLoader).forEach { loader ->
        var current = loader
        while (current != null && visited.add(current)) {
            if (current is URLClassLoader) current.getURLs().forEach(::addUrl)
            current = current.parent
        }
    }
    System.getProperty("java.class.path").orEmpty().split(File.pathSeparatorChar)
        .filter { it.isNotBlank() }.forEach { entries.add(Path.of(it).toAbsolutePath().normalize()) }
    check(entries.isNotEmpty()) { "Could not resolve the lease helper's runtime classpath" }
    return entries.joinToString(File.pathSeparator)
}

/** Test-only entry point: every acquisition and release goes through the production Kotlin lease. */
object DesktopScheduledLeaderLeaseProcessHelper {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 2) { "Expected stable lock path and process role" }
        val lease = DesktopScheduledLeaderLease(Path.of(args[0]))
        val scope = ScheduledScope(HostId("lease-process-${args[1]}"), null, 1)
        val input = System.`in`.bufferedReader(Charsets.UTF_8)
        fun reply(value: String) {
            println(value)
            System.out.flush()
        }
        runBlocking {
            try {
                reply("READY ${ProcessHandle.current().pid()}")
                while (true) {
                    // The parent bounds both command writes and reply reads and kills
                    // every child on failure; EOF is an error, never an implicit success.
                    when (val command = input.readLine() ?: error("Parent stdin closed before EXIT")) {
                        "ACQUIRE" -> reply("ACQUIRE ${lease.acquire(scope)} ${lease.isHeld()}")
                        "HELD" -> reply("HELD ${lease.isHeld()}")
                        "RELEASE" -> {
                            lease.release()
                            check(!lease.isHeld())
                            reply("RELEASE false")
                        }
                        "EXIT" -> {
                            check(!lease.isHeld()) { "An explicit RELEASE is required before normal EXIT" }
                            reply("BYE")
                            break
                        }
                        else -> error("Unexpected lease helper command: $command")
                    }
                }
            } finally {
                lease.release()
            }
        }
    }
}
