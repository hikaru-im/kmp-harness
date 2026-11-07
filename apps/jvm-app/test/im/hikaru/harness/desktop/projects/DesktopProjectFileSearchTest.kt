package im.hikaru.harness.desktop.projects

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.desktop.connection.LocalConnection
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DesktopProjectFileSearchTest {
    @Test fun searchFuzzyMatchesAllTermsAgainstRelativePathsIgnoringCase() = runTest {
        val f = fixture()
        try {
            Files.createDirectories(f.root.resolve("src/main"))
            Files.createDirectories(f.root.resolve("src/test"))
            Files.writeString(f.root.resolve("src/main/ProjectFileSearch.kt"), "main")
            Files.writeString(f.root.resolve("src/test/ProjectFileSearchTest.kt"), "test")
            Files.writeString(f.root.resolve("src/main/Other.kt"), "ProjectFileSearch")
            assertEquals(listOf("src/main/ProjectFileSearch.kt", "src/test/ProjectFileSearchTest.kt"),
                f.search.search(f.target, "  SRC\tPFSKT  ").files.map { it.path })
            assertEquals(listOf("src/main/ProjectFileSearch.kt"),
                f.search.search(f.target, "pfs mn").files.map { it.path })
            assertTrue(f.search.search(f.target, "pfs missing").files.isEmpty())
            assertTrue(f.search.search(f.target, "sfpk").files.isEmpty())
        } finally { f.close() }
    }

    @Test fun searchOnlyIncludesSelectedProjectRoot() = runTest {
        val f = fixture()
        try {
            Files.writeString(f.root.resolve("selected.txt"), "selected")
            Files.writeString(f.directory.resolve("outside.txt"), "outside")
            val sibling = Files.createDirectory(f.directory.resolve("other-root"))
            Files.writeString(sibling.resolve("other.txt"), "other")
            assertIs<LocalProjectWriteResult.Committed>(f.repository.create(f.binding,
                CreateLocalProject("other", "Other", sibling.toString())))
            val result = f.search.search(f.target, "")
            assertEquals(listOf("selected.txt"), result.files.map { it.path })
            assertFalse(result.truncated)
        } finally { f.close() }
    }

    @Test fun searchExcludesGitDirectoriesAndSymbolicLinks() = runTest {
        val f = fixture()
        try {
            val source = Files.createDirectory(f.root.resolve("src"))
            Files.writeString(source.resolve("visible.txt"), "visible")
            for (parent in listOf(f.root, source)) {
                val git = Files.createDirectory(parent.resolve(".git"))
                Files.writeString(git.resolve("config"), "hidden")
            }
            val outside = Files.createDirectory(f.directory.resolve("outside"))
            val secret = Files.writeString(outside.resolve("secret.txt"), "secret")
            Files.createSymbolicLink(f.root.resolve("linked-file.txt"), source.resolve("visible.txt"))
            Files.createSymbolicLink(f.root.resolve("external-file.txt"), secret)
            Files.createSymbolicLink(f.root.resolve("linked-directory"), source)
            Files.createSymbolicLink(f.root.resolve("external-directory"), outside)
            assertEquals(listOf("src/visible.txt"), f.search.search(f.target, "").files.map { it.path })
        } finally { f.close() }
    }

    @Test fun previewReadsUtf8AndAcceptsSizeLimit() = runTest {
        val f = fixture()
        try {
            val text = "项目文件预览 — café\nsecond line\n"
            Files.writeString(f.root.resolve("unicode.txt"), text)
            Files.write(f.root.resolve("limit.txt"), ByteArray(512 * 1024) { 'a'.code.toByte() })
            Files.write(f.root.resolve("empty.txt"), byteArrayOf())
            assertEquals(text, f.search.read(f.target, ProjectFileMatch("unicode.txt")))
            assertEquals("a".repeat(512 * 1024), f.search.read(f.target, ProjectFileMatch("limit.txt")))
            assertEquals("", f.search.read(f.target, ProjectFileMatch("empty.txt")))
        } finally { f.close() }
    }

    @Test fun previewRejectsOutsideAndAbsolutePaths() = runTest {
        val f = fixture()
        try {
            val inside = Files.writeString(f.root.resolve("inside.txt"), "inside")
            val outside = Files.writeString(f.directory.resolve("outside.txt"), "outside")
            for (path in listOf("../outside.txt", "nested/../../outside.txt", inside.toString(), outside.toString())) {
                assertFailsWith<IllegalStateException>(path) { f.search.read(f.target, ProjectFileMatch(path)) }
            }
        } finally { f.close() }
    }

    @Test fun previewRejectsSymbolicLinkFilesAndParentDirectories() = runTest {
        val f = fixture()
        try {
            val source = Files.createDirectory(f.root.resolve("src"))
            Files.writeString(source.resolve("file.txt"), "inside")
            val outside = Files.createDirectory(f.directory.resolve("outside"))
            Files.writeString(outside.resolve("file.txt"), "outside")
            Files.createSymbolicLink(f.root.resolve("inside-link.txt"), source.resolve("file.txt"))
            Files.createSymbolicLink(f.root.resolve("outside-link.txt"), outside.resolve("file.txt"))
            Files.createSymbolicLink(f.root.resolve("inside-directory"), source)
            Files.createSymbolicLink(f.root.resolve("outside-directory"), outside)
            for (path in listOf("inside-link.txt", "outside-link.txt", "inside-directory/file.txt", "outside-directory/file.txt")) {
                assertEquals("暂不预览符号链接文件。", assertFailsWith<IllegalStateException>(path) {
                    f.search.read(f.target, ProjectFileMatch(path))
                }.message)
            }
        } finally { f.close() }
    }

    @Test fun previewRejectsOversizeFiles() = runTest {
        val f = fixture()
        try {
            Files.write(f.root.resolve("large.txt"), ByteArray(512 * 1024 + 1) { 'a'.code.toByte() })
            assertEquals("文件超过 512 KiB，暂不支持预览。", assertFailsWith<IllegalStateException> {
                f.search.read(f.target, ProjectFileMatch("large.txt"))
            }.message)
        } finally { f.close() }
    }

    @Test fun previewRejectsBinaryFiles() = runTest {
        val f = fixture()
        try {
            Files.write(f.root.resolve("binary.dat"), byteArrayOf(65, 0, 66))
            assertEquals("暂不支持二进制文件预览。", assertFailsWith<IllegalStateException> {
                f.search.read(f.target, ProjectFileMatch("binary.dat"))
            }.message)
        } finally { f.close() }
    }

    @Test fun previewRejectsInvalidUtf8() = runTest {
        val f = fixture()
        try {
            Files.write(f.root.resolve("invalid.txt"), byteArrayOf(0xc3.toByte(), 0x28))
            assertEquals("暂不支持非 UTF-8 文件预览。", assertFailsWith<IllegalStateException> {
                f.search.read(f.target, ProjectFileMatch("invalid.txt"))
            }.message)
        } finally { f.close() }
    }

    @Test fun searchAndPreviewRejectRelayForeignHostAndForeignConnectionBindings() = runTest {
        val f = fixture()
        try {
            Files.writeString(f.root.resolve("file.txt"), "local")
            for (binding in listOf(
                f.binding.copy(transport = ConnectionTransport.Relay),
                f.binding.copy(scope = LocalProjectScope(HostId("foreign"), 1)),
                f.binding.copy(connection = LocalConnection(f.gateway)),
            )) {
                val target = f.target.copy(binding = binding)
                assertFailsWith<IllegalStateException> { f.search.search(target, "") }
                assertFailsWith<IllegalStateException> { f.search.read(target, ProjectFileMatch("file.txt")) }
            }
            assertEquals("local", f.search.read(f.target, ProjectFileMatch("file.txt")))
        } finally { f.close() }
    }

    @Test fun searchCapsResultsAndReportsTruncation() = runTest {
        val f = fixture()
        try {
            val names = (0..200).map { "file-${it.toString().padStart(3, '0')}.txt" }
            for (name in names.take(199)) Files.writeString(f.root.resolve(name), "file")
            val complete = f.search.search(f.target, "file")
            assertEquals(names.take(199), complete.files.map { it.path })
            assertFalse(complete.truncated)
            for (name in names.drop(199)) Files.writeString(f.root.resolve(name), "file")
            val truncated = f.search.search(f.target, "file")
            val paths = truncated.files.map { it.path }
            assertEquals(200, paths.size)
            assertEquals(200, paths.toSet().size)
            assertEquals(paths.sorted(), paths)
            assertTrue(names.containsAll(paths))
            assertTrue(truncated.truncated)
        } finally { f.close() }
    }

    private suspend fun fixture(): Fixture {
        val f = Fixture(Files.createTempDirectory("project-file-search-").toRealPath())
        f.gateway.register(HostDescribeEndpoint) {
            ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current, f.host, "Real local test host"))
        }
        val created = assertIs<LocalProjectWriteResult.Committed>(f.repository.create(f.binding,
            CreateLocalProject("one", "Project", f.root.toString())))
        f.target = ProjectFileSearchTarget(f.binding, created.snapshot.projects.single())
        return f
    }

    private class Fixture(val directory: Path) {
        val root: Path = Files.createDirectory(directory.resolve("existing-root"))
        val host = HostId("local-project-host")
        val gateway = ApiGateway()
        val connection = LocalConnection(gateway)
        val binding = LocalProjectBinding(connection, LocalProjectScope(host, 1), ConnectionTransport.Local)
        val repository = DesktopLocalProjectRepository(host, DesktopLocalProjectStore(directory.resolve("projects.json")), expectedConnection = connection)
        val search = DesktopProjectFileSearch(repository)
        lateinit var target: ProjectFileSearchTarget
        fun close() { directory.toFile().deleteRecursively() }
    }
}
