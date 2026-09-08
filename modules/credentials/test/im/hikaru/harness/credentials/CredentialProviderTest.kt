package im.hikaru.harness.credentials

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CredentialProviderTest {
    @Test
    fun inMemoryProviderResolvesAndNotifies() =
        runTest {
            val provider = InMemoryCredentialProvider()
            val reference = credentialRef("TEST_API_KEY")
            val updated = mutableListOf<String>()
            val registration =
                provider.watch(
                    CredentialUpdateListener { updated += it.name },
                )

            provider.set(reference, "secret")

            assertEquals("secret", provider.resolveRequired(reference))
            assertEquals(
                CredentialInfo(configured = true, source = "memory", writable = true),
                provider.describe(reference),
            )
            assertEquals(listOf("TEST_API_KEY"), updated)

            provider.unset(reference)
            assertEquals(null, provider.resolve(reference))
            registration.dispose()
            provider.dispose()
        }

    @Test
    fun emptyValuesAndInvalidReferencesFail() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                credentialRef("not valid")
            }
            val provider = InMemoryCredentialProvider()
            assertFailsWith<IllegalArgumentException> {
                provider.set(credentialRef("TEST_API_KEY"), "")
            }
            provider.dispose()
        }
}
