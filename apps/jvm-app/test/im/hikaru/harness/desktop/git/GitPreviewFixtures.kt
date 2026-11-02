package im.hikaru.harness.desktop.git

/** Real decoder input for the image/SVG/PDF preview tests: a 1x1 PNG, a tiny SVG and a one-page PDF. */
object GitPreviewFixtures {
    /** A 1x1 opaque red PNG. */
    fun png(): ByteArray = hex(
        "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489" +
            "0000000d49444154789c63f8cfc0f01f00050001ff89993d1d0000000049454e44ae426082",
    )

    fun svg(width: Int = 24, height: Int = 16): ByteArray =
        """<svg xmlns="http://www.w3.org/2000/svg" width="$width" height="$height">
            <rect width="$width" height="$height" fill="#f00"/>
        </svg>""".trimIndent().toByteArray()

    /**
     * A single-page 200x100pt PDF. PDFBox rejects a hand-counted cross-reference table, so the
     * offsets are recorded while the body is assembled instead of being hard-coded.
     */
    fun pdf(): ByteArray {
        val content = "BT /F1 24 Tf 20 40 Td (Hello) Tj ET\n"
        val bodies = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 100] /Contents 4 0 R " +
                "/Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length ${content.length} >>\nstream\n$content" + "endstream",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        )
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        bodies.forEachIndexed { index, body ->
            offsets += out.length
            out.append("${index + 1} 0 obj\n").append(body).append("\nendobj\n")
        }
        val xrefOffset = out.length
        out.append("xref\n0 ${bodies.size + 1}\n")
        out.append("0000000000 65535 f \n")
        offsets.forEach { out.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
        out.append("trailer\n<< /Size ${bodies.size + 1} /Root 1 0 R >>\n")
        out.append("startxref\n$xrefOffset\n%%EOF\n")
        return out.toString().toByteArray(Charsets.US_ASCII)
    }

    private fun hex(value: String): ByteArray =
        value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
