package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared identity artwork; the owner supplies the surrounding button and interaction state. */
@Composable
public fun HarnessAvatar(initials: String?, modifier: Modifier = Modifier, size: Dp = HarnessTokens.Control.RailAvatar) {
    val label = initials?.takeIf { it.isNotBlank() }
    Box(modifier.size(size).clip(CircleShape).background(
        if (label == null) HarnessTokens.Colors.SurfaceSelected else HarnessTokens.Identity.AvatarSurface,
    ), contentAlignment = Alignment.Center) {
        if (label == null) HarnessIcon(HarnessIconName.User, tint = HarnessTokens.Colors.Text, size = 14.dp)
        else HarnessText(label, color = HarnessTokens.Identity.OnAvatar,
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
