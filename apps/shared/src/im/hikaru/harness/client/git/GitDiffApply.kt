package im.hikaru.harness.client.git

/**
 * Builds a pasteable command for applying the complete diff returned by the Harness Git read.
 *
 * The original command form and its exact failure rules were not observed in the ASAR. This
 * heredoc form is therefore a Harness choice: it keeps the patch in the clipboard command and
 * chooses a delimiter that does not occur as a complete patch line.
 */
public fun gitApplyCommand(patch: String): String? {
    if (patch.isBlank() || patch.lineSequence().none { it.startsWith("diff --git ") }) return null

    val prefix = "HARNESS_GIT_APPLY_EOF"
    var delimiter = prefix
    var suffix = 0
    while (patch.lineSequence().any { it == delimiter }) {
        suffix++
        delimiter = "${prefix}_$suffix"
    }
    val body = if (patch.endsWith("\n")) patch else "$patch\n"
    return "git apply <<'$delimiter'\n$body$delimiter"
}
