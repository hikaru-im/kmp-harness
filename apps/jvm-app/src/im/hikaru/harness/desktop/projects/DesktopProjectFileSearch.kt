package im.hikaru.harness.desktop.projects

import im.hikaru.harness.client.projects.*
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

public class DesktopProjectFileSearch(private val projects: LocalProjectRepository) : ProjectFileSearch {
    private suspend fun root(target: ProjectFileSearchTarget): Path {
        val result = projects.validateRoot(target.binding, target.project.id, target.project.roots.single())
        check(result is LocalProjectRootResult.Ready) { "项目目录不可访问，请刷新项目后重试。" }
        return Path.of(result.target.root.path).toRealPath()
    }

    override suspend fun search(target: ProjectFileSearchTarget, query: String): ProjectFileSearchResult = withContext(Dispatchers.IO) {
        val root = root(target)
        val matches = mutableListOf<ProjectFileMatch>()
        val pending = ArrayDeque<Path>().apply { add(root) }
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        var visited = 0
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = pending.removeFirst()
            check(directory.toRealPath().startsWith(root)) { "目录已发生变化，请重试。" }
            Files.newDirectoryStream(directory).use { children ->
                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    if (++visited > 50_000) return@withContext ProjectFileSearchResult(matches.sortedBy { it.path }, true)
                    if (Files.isSymbolicLink(child)) continue
                    if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                        if (child.fileName.toString() != ".git") pending.add(child)
                    } else if (Files.isRegularFile(child, NOFOLLOW_LINKS)) {
                        val relative = root.relativize(child).toString().replace('\\', '/')
                        val path = relative.lowercase()
                        if (terms.all { fuzzyMatches(path, it) }) {
                            matches.add(ProjectFileMatch(relative))
                            if (matches.size == 200) return@withContext ProjectFileSearchResult(matches.sortedBy { it.path }, true)
                        }
                    }
                }
            }
        }
        ProjectFileSearchResult(matches.sortedBy { it.path })
    }

    override suspend fun read(target: ProjectFileSearchTarget, file: ProjectFileMatch): String = withContext(Dispatchers.IO) {
        val root = root(target)
        val relative = Path.of(file.path)
        check(!relative.isAbsolute && relative.nameCount > 0) { "文件路径无效。" }
        val path = root.resolve(relative).normalize()
        check(path.startsWith(root) && path != root) { "文件不在当前项目内。" }
        var cursor = root
        for (part in root.relativize(path)) {
            cursor = cursor.resolve(part)
            check(!Files.isSymbolicLink(cursor)) { "暂不预览符号链接文件。" }
        }
        check(path.toRealPath().startsWith(root) && Files.isRegularFile(path, NOFOLLOW_LINKS)) { "文件已移动或不可读取。" }
        val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { it.readNBytes(512 * 1024 + 1) }
        check(bytes.size <= 512 * 1024) { "文件超过 512 KiB，暂不支持预览。" }
        check(bytes.none { it == 0.toByte() }) { "暂不支持二进制文件预览。" }
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            error("暂不支持非 UTF-8 文件预览。")
        }
    }

    private fun fuzzyMatches(path: String, query: String): Boolean {
        var index = 0
        for (character in query) {
            index = path.indexOf(character, index)
            if (index < 0) return false
            index++
        }
        return true
    }
}
