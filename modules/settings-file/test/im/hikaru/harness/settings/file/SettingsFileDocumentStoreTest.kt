@file:OptIn(kotlin.io.path.ExperimentalPathApi::class)

package im.hikaru.harness.settings.file

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsFileDocumentStoreTest {
    @Test
    fun yamlRoundTripsThroughAnAtomicOwnerOnlyFile() =
        runTest {
            val directory = Files.createTempDirectory("harness-settings")
            try {
                val path = directory.resolve("settings.yaml")
                val store =
                    FileSettingsDocumentStore(
                        SettingsFileConfig(path = path, watch = false),
                    )
                store.write(
                    buildJsonObject {
                        putJsonObject("llm-koog") {
                            put("enabled", true)
                        }
                    },
                )
                val loaded = store.read()
                assertEquals(
                    "true",
                    loaded["llm-koog"]!!.jsonObject["enabled"]!!.jsonPrimitive.content,
                )
                val permissions = Files.getPosixFilePermissions(path)
                assertTrue(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ in permissions,
                )
                assertTrue(
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE in permissions,
                )
                assertFalse(
                    permissions.any {
                        it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_")
                    },
                )
                store.dispose()
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun missingYamlShouldInitializeAsAnOwnerOnlyEmptyDocument() =
        runTest {
            val directory = Files.createTempDirectory("harness-settings-empty")
            try {
                val path = directory.resolve("settings.yaml")
                val store =
                    FileSettingsDocumentStore(
                        SettingsFileConfig(path = path, watch = false),
                    )

                assertEquals(0, store.read().size)
                assertTrue(Files.exists(path))
                assertFalse(
                    Files.getPosixFilePermissions(path).any {
                        it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_")
                    },
                )
                store.dispose()
            } finally {
                directory.deleteRecursively()
            }
        }

    @Test
    fun jsonRootMustBeAnObject() =
        runTest {
            val directory = Files.createTempDirectory("harness-settings")
            try {
                val path = directory.resolve("settings.json")
                Files.writeString(path, "[]")
                Files.setPosixFilePermissions(
                    path,
                    setOf(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    ),
                )
                val store =
                    FileSettingsDocumentStore(
                        SettingsFileConfig(path = path, watch = false),
                    )
                assertFailsWith<IllegalStateException> {
                    store.read()
                }
                store.dispose()
            } finally {
                directory.deleteRecursively()
            }
        }
}
