@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package im.hikaru.harness.credentials.local

import im.hikaru.harness.credentials.credentialRef
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalCredentialsProviderTest {
    @Test
    fun precedenceIsEnvironmentThenManagedFileThenDotEnv() =
        runTest {
            val home = Files.createTempDirectory("harness-credentials")
            val project = Files.createTempDirectory("harness-project")
            try {
                Files.writeString(
                    project.resolve(".env"),
                    "PROJECT_ONLY=project\nSHADOWED=project\n",
                )
                Files.writeString(
                    home.resolve(".env"),
                    "USER_ONLY=user\nSHADOWED=user\n",
                )
                val provider =
                    LocalCredentialsProvider(
                        LocalCredentialsConfig(
                            path = home.resolve(".credentials.yaml"),
                            projectDir = project,
                            userEnvPath = home.resolve(".env"),
                            environment = { name ->
                                if (name == "ENV_ONLY") "environment" else null
                            },
                            watch = false,
                        ),
                    )
                val managed = credentialRef("MANAGED")
                provider.set(managed, "file")

                assertEquals("environment", provider.resolve(credentialRef("ENV_ONLY"))!!.value)
                assertEquals("file", provider.resolve(managed)!!.value)
                assertEquals("project", provider.resolve(credentialRef("PROJECT_ONLY"))!!.value)
                assertEquals("user", provider.resolve(credentialRef("USER_ONLY"))!!.value)

                assertFailsWith<IllegalArgumentException> {
                    provider.set(credentialRef("ENV_ONLY"), "cannot-shadow")
                }
                provider.dispose()
            } finally {
                home.deleteRecursively()
                project.deleteRecursively()
            }
        }

    @Test
    fun managedValuesCanBeRemoved() =
        runTest {
            val home = Files.createTempDirectory("harness-credentials")
            try {
                val provider =
                    LocalCredentialsProvider(
                        LocalCredentialsConfig(
                            path = home.resolve(".credentials.yaml"),
                            watch = false,
                        ),
                    )
                val reference = credentialRef("REMOVE_ME")
                assertFalse(Files.exists(home.resolve(".credentials.yaml")))
                provider.set(reference, "secret")
                val path = home.resolve(".credentials.yaml")
                assertTrue(Files.exists(path))
                assertFalse(
                    Files.getPosixFilePermissions(path).any {
                        it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_")
                    },
                )
                assertEquals("secret", provider.resolve(reference)!!.value)
                provider.unset(reference)
                assertEquals(null, provider.resolve(reference))
                provider.dispose()
            } finally {
                home.deleteRecursively()
            }
        }

    @Test
    fun dshDocumentIsAFlatReferenceMapping() =
        runTest {
            val home = Files.createTempDirectory("harness-credentials")
            try {
                val path = home.resolve(".credentials.yaml")
                Files.writeString(
                    path,
                    "OPENAI_API_KEY: flat-secret\n",
                )
                Files.setPosixFilePermissions(
                    path,
                    setOf(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    ),
                )

                val provider =
                    LocalCredentialsProvider(
                        LocalCredentialsConfig(path = path, watch = false),
                    )
                assertEquals(
                    "flat-secret",
                    provider.resolve(credentialRef("OPENAI_API_KEY"))?.value,
                )
                provider.dispose()
            } finally {
                home.deleteRecursively()
            }
        }

    @Test
    fun dshDocumentRejectsVersionRefsEnvelope() =
        runTest {
            val home = Files.createTempDirectory("harness-credentials")
            try {
                val path = home.resolve(".credentials.yaml")
                Files.writeString(
                    path,
                    "version: 1\nrefs:\n  OPENAI_API_KEY: envelope-secret\n",
                )
                Files.setPosixFilePermissions(
                    path,
                    setOf(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    ),
                )

                assertFailsWith<IllegalArgumentException> {
                    LocalCredentialsProvider(
                        LocalCredentialsConfig(path = path, watch = false),
                    )
                }
            } finally {
                home.deleteRecursively()
            }
        }
}
