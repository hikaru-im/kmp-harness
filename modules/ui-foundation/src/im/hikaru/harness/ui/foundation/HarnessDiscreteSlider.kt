package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.*
import kotlin.math.roundToInt

/** Theme-owned, keyboard-accessible discrete selection. The caller supplies supported steps only. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
public fun HarnessDiscreteSlider(
    labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    description: String, enabled: Boolean = true, modifier: Modifier = Modifier, testTag: String,
) {
    val canChange = enabled && labels.size > 1
    val index = selectedIndex.coerceIn(0, (labels.size - 1).coerceAtLeast(0))
    val active = if (canChange) HarnessTokens.Colors.Accent else HarnessTokens.Colors.TextDisabled
    val inactive = HarnessTokens.Colors.SurfaceSelected
    val thumb = HarnessTokens.Colors.Surface
    val tick = HarnessTokens.Colors.TextMuted
    Column(modifier) {
        Slider(
            value = index.toFloat(), onValueChange = { if (canChange) onSelect(it.roundToInt().coerceIn(labels.indices)) },
            enabled = canChange, valueRange = 0f..(labels.size - 1).coerceAtLeast(1).toFloat(),
            steps = (labels.size - 2).coerceAtLeast(0),
            thumb = {
                Box(Modifier.size(HarnessTokens.Slider.ThumbSize)
                    .shadow(HarnessTokens.Slider.ThumbElevation, CircleShape)
                    .background(thumb, CircleShape))
            },
            track = {
                Canvas(Modifier.fillMaxWidth().height(HarnessTokens.Slider.TrackHeight)) {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(inactive, cornerRadius = radius)
                    val fraction = index.toFloat() / (labels.size - 1).coerceAtLeast(1)
                    if (fraction > 0) drawRoundRect(active, size = Size(size.width * fraction, size.height), cornerRadius = radius)
                    if (labels.size > 1) labels.indices.forEach { step ->
                        drawCircle(if (step <= index) thumb else tick, HarnessTokens.Slider.TickRadius.toPx(),
                            Offset(size.width * step / labels.lastIndex, size.height / 2))
                    }
                }
            },
            colors = SliderDefaults.colors(thumbColor = HarnessTokens.Colors.Accent,
                activeTrackColor = HarnessTokens.Colors.Accent, inactiveTrackColor = HarnessTokens.Colors.SurfaceSelected),
            modifier = Modifier.fillMaxWidth().semantics {
                this.testTag = testTag; contentDescription = description
                stateDescription = labels.getOrNull(index) ?: "不可用"
            },
        )
        if (labels.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HarnessText(labels.first(), style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
            if (labels.size > 1) HarnessText(labels.last(), style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
        }
    }
}
