package im.hikaru.harness.ui.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HarnessScrollbarTest {
    @Test
    fun arrowlessGeometryUsesTheWholeTrack() {
        val geometry = harnessScrollbarGeometry(
            containerExtent = 100f,
            metrics = HarnessScrollbarMetrics(positionFraction = 0.5f, thumbFraction = 0.25f),
        )

        assertEquals(0f, geometry.trackStart)
        assertEquals(100f, geometry.trackLength)
        assertEquals(25f, geometry.thumbExtent)
        assertEquals(37.5f, geometry.thumbTop)
        assertEquals(75f, geometry.thumbTravel)
    }

    @Test
    fun arrowedGeometryReservesAndAlignsBothArrows() {
        val geometry = harnessScrollbarGeometry(
            containerExtent = 100f,
            metrics = HarnessScrollbarMetrics(positionFraction = 0f, thumbFraction = 0.2f),
            variant = HarnessScrollbarVariant.Arrowed,
            arrowExtent = 18f,
            minimumThumbExtent = 16f,
        )

        assertEquals(18f, geometry.trackStart)
        assertEquals(64f, geometry.trackLength)
        assertEquals(18f, geometry.thumbTop)
        assertEquals(16f, geometry.thumbExtent)
        assertEquals(82f, geometry.trackEnd)
        assertEquals(18f, geometry.arrowExtent)
    }

    @Test
    fun geometryClampsSmallContainersAndOutOfRangeMetrics() {
        val geometry = harnessScrollbarGeometry(
            containerExtent = 10f,
            metrics = HarnessScrollbarMetrics(positionFraction = 4f, thumbFraction = -1f),
            variant = HarnessScrollbarVariant.Arrowed,
            arrowExtent = 18f,
            minimumThumbExtent = 16f,
        )

        assertEquals(5f, geometry.trackStart)
        assertEquals(0f, geometry.trackLength)
        assertEquals(0f, geometry.thumbExtent)
        assertEquals(5f, geometry.thumbTop)
    }

    @Test
    fun hitTargetsDistinguishArrowsPagesAndThumb() {
        val geometry = harnessScrollbarGeometry(
            containerExtent = 120f,
            metrics = HarnessScrollbarMetrics(positionFraction = 0.5f, thumbFraction = 0.25f),
            variant = HarnessScrollbarVariant.Arrowed,
        )

        assertEquals(HarnessScrollbarHitTarget.StepBackward, geometry.hitTarget(2f, HarnessScrollbarVariant.Arrowed))
        assertEquals(HarnessScrollbarHitTarget.PageBackward, geometry.hitTarget(30f, HarnessScrollbarVariant.Arrowed))
        assertEquals(HarnessScrollbarHitTarget.Thumb, geometry.hitTarget(60f, HarnessScrollbarVariant.Arrowed))
        assertEquals(HarnessScrollbarHitTarget.PageForward, geometry.hitTarget(100f, HarnessScrollbarVariant.Arrowed))
        assertEquals(HarnessScrollbarHitTarget.StepForward, geometry.hitTarget(119f, HarnessScrollbarVariant.Arrowed))
        assertTrue(geometry.hitTarget(2f, HarnessScrollbarVariant.Arrowless) != HarnessScrollbarHitTarget.StepBackward)
    }

    @Test
    fun scrollStateMetricsHideWhenThereIsNoRange() {
        val metrics = harnessScrollbarMetrics(value = 0, maxValue = 0, viewportSize = 400)
        assertEquals(0f, metrics.positionFraction)
        assertEquals(1f, metrics.thumbFraction)
        assertEquals(false, metrics.visible)
    }

    @Test
    fun scrollStateMetricsMapPositionAndViewport() {
        val metrics = harnessScrollbarMetrics(value = 100, maxValue = 300, viewportSize = 200)
        assertEquals(1f / 3f, metrics.positionFraction)
        assertEquals(0.4f, metrics.thumbFraction)
        assertEquals(true, metrics.visible)
    }
}
