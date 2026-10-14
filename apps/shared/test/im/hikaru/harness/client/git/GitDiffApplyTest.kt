package im.hikaru.harness.client.git

import kotlin.test.*

/** Harness choice for turning a complete unified diff into a pasteable `git apply` command. */
class GitDiffApplyTest {
    @Test
    fun completePatchBecomesACommandContainingTheWholePatch() {
        val patch = "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,1 +1,1 @@\n" +
            "-old\n" +
            "+new\n"

        val command = assertNotNull(gitApplyCommand(patch))

        assertTrue(command.startsWith("git apply <<'"))
        assertTrue(command.contains(patch))
        assertTrue(command.endsWith("HARNESS_GIT_APPLY_EOF"))
    }

    @Test
    fun emptyPatchCannotBecomeACommand() {
        assertNull(gitApplyCommand(""))
        assertNull(gitApplyCommand(" \n\n"))
    }

    @Test
    fun metadataWithoutDiffHeaderCannotBecomeACommand() {
        val metaOnlyPatch = "new file mode 100644\n" +
            "index 0000000..1111111\n"

        assertNull(gitApplyCommand(metaOnlyPatch))
    }

    @Test
    fun delimiterIsChangedWhenItAppearsAsPatchContent() {
        val patch = "diff --git a/file.txt b/file.txt\n" +
            "--- a/file.txt\n" +
            "+++ b/file.txt\n" +
            "@@ -1,1 +1,1 @@\n" +
            "-old\n" +
            "HARNESS_GIT_APPLY_EOF\n"

        val command = assertNotNull(gitApplyCommand(patch))

        assertTrue(command.contains(patch))
        assertTrue(command.endsWith("HARNESS_GIT_APPLY_EOF_1"))
    }
}
