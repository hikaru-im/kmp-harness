package im.hikaru.harness.client.panels

public enum class ThreadPanelWidthMode { Regular, Unified, Full }

/** Dimensions are logical px (Compose dp at the host boundary), not physical screen pixels. */
public object ThreadPanelLayout {
    public fun preferredWidth(mainWidth: Float, height: Float, defaultWidth: Float = 600f): Float {
        require(mainWidth.isFinite() && mainWidth >= 0f && height.isFinite() && height >= 0f)
        return if (defaultWidth == 600f) {
            maxOf(320f, minOf(height * 1.6f, mainWidth - 500f), minOf(640f, mainWidth - 352f))
        } else defaultWidth
    }

    public fun maximumWidth(mainWidth: Float, mode: ThreadPanelWidthMode): Float {
        require(mainWidth.isFinite() && mainWidth >= 0f)
        return maxOf(320f, mainWidth - when (mode) {
            ThreadPanelWidthMode.Regular -> 352f
            ThreadPanelWidthMode.Unified -> 320f
            ThreadPanelWidthMode.Full -> 0f
        })
    }

    /** Reference 320 minimum is capped by the actual compact host's available width. */
    public fun resolveWidth(
        mainWidth: Float,
        height: Float,
        availableWidth: Float,
        mode: ThreadPanelWidthMode = ThreadPanelWidthMode.Regular,
        requestedWidth: Float = preferredWidth(mainWidth, height),
    ): Float {
        require(availableWidth.isFinite() && availableWidth >= 0f)
        val maximum = maximumWidth(mainWidth, mode)
        val width = if (requestedWidth.isFinite()) requestedWidth else 600f
        return minOf(availableWidth, maxOf(minOf(320f, maximum), minOf(width, maximum)))
    }
}
