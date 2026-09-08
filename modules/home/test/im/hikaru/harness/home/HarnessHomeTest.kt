package im.hikaru.harness.home

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HarnessHomeTest {
    @Test
    fun shouldNormalizeOneRootAndResolveEveryStandardFile() {
        val home = HarnessHome.at(Path.of("root", "child", ".."))

        assertEquals(Path.of("root").toAbsolutePath().normalize(), home.directory)
        assertEquals(home.directory.resolve("package.json"), home.manifest)
        assertEquals(home.directory.resolve("cordis.yml"), home.profileRoot)
        assertEquals(home.directory.resolve("cordis.patch.yml"), home.profilePatch)
        assertEquals(home.directory.resolve("settings.yaml"), home.settings)
        assertEquals(home.directory.resolve(".credentials.yaml"), home.credentials)
        assertEquals(home.directory.resolve(".env"), home.userEnvironment)
    }

    @Test
    fun arbitraryChildrenMustRemainDirectlyBelowTheHome() {
        val home = HarnessHome.at(Path.of("root"))

        assertFailsWith<IllegalArgumentException> { home.resolve("") }
        assertFailsWith<IllegalArgumentException> { home.resolve("nested/file") }
        assertFailsWith<IllegalArgumentException> { home.resolve("..") }
    }
}
