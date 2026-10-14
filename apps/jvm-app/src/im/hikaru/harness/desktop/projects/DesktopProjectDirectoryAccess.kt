package im.hikaru.harness.desktop.projects

import im.hikaru.harness.client.projects.LocalProjectFailure
import im.hikaru.harness.client.projects.LocalProjectRoot
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

public sealed interface DesktopProjectDirectoryResult {
    public data class Ready(val root: LocalProjectRoot) : DesktopProjectDirectoryResult
    public data class Failed(val failure: LocalProjectFailure) : DesktopProjectDirectoryResult
}

/** Existing directories only. Never expands '~', falls back to cwd, or creates a directory. */
public class DesktopProjectDirectoryAccess {
    public suspend fun validate(directory: String): DesktopProjectDirectoryResult = withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            if (directory.isBlank()) return@withContext rejected("目录不能为空。")
            val requested = Path.of(directory)
            if (!requested.isAbsolute) return@withContext rejected("请选择已有目录的绝对路径。")
            val canonical = requested.toRealPath()
            val attributes = Files.readAttributes(canonical, BasicFileAttributes::class.java)
            if (!attributes.isDirectory) return@withContext rejected("路径不是目录。")
            // Opening and listing exercises actual directory access; exists/isReadable cannot report its cause.
            Files.newDirectoryStream(canonical).use { it.iterator().hasNext() }
            currentCoroutineContext().ensureActive()
            DesktopProjectDirectoryResult.Ready(LocalProjectRoot(canonical.toString()))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: AccessDeniedException) { denied() }
        catch (_: SecurityException) { denied() }
        catch (_: InvalidPathException) { rejected("目录路径无效。") }
        catch (_: NoSuchFileException) { rejected("目录不存在；请重新选择已有目录。") }
        catch (_: NotDirectoryException) { rejected("路径不是目录。") }
        catch (_: Exception) { DesktopProjectDirectoryResult.Failed(LocalProjectFailure.Error("无法读取项目目录。")) }
    }

    private fun rejected(message: String) = DesktopProjectDirectoryResult.Failed(LocalProjectFailure.Rejected(message))
    private fun denied() = DesktopProjectDirectoryResult.Failed(LocalProjectFailure.AccessDenied("没有读取项目目录的权限。"))
}
