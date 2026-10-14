package im.hikaru.harness.desktop.scheduling

import im.hikaru.harness.client.scheduling.*
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Caller supplies a stable file under the same local application home in every process.
 * Never delete/replace this file: doing so would allow locking two different inodes. */
public class DesktopScheduledLeaderLease(path: Path) : ScheduledLeaderLease {
    private val path = path.toAbsolutePath().normalize()
    @Volatile private var held: FileLock? = null
    private var channel: FileChannel? = null
    override fun isHeld(): Boolean = held?.isValid == true

    override suspend fun acquire(scope: ScheduledScope): Boolean = withContext(Dispatchers.IO) {
        synchronized(this@DesktopScheduledLeaderLease) {
            if (channel != null) return@synchronized false
            Files.createDirectories(path.parent)
            val opened = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            try {
                val lock = try { opened.tryLock() } catch (_: OverlappingFileLockException) { null }
                if (lock == null) { opened.close(); false } else { channel = opened; held = lock; true }
            } catch (error: Exception) { opened.close(); throw error }
        }
    }

    override suspend fun release(): Unit = withContext(Dispatchers.IO) {
        synchronized(this@DesktopScheduledLeaderLease) {
            held?.release()
            held = null
            channel?.close()
            channel = null
        }
    }
}
