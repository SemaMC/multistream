package com.multistream.feature.streamingui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.multistream.core.common.Layout
import com.multistream.core.common.Widget

/**
 * Aspect-correct preview of one layout and a summary of its widgets.
 *
 * Horizontal uses 16:9 (1920x1080) and vertical uses 9:16 (1080x1920), matching the resolution
 * each encoder is configured with.
 */
@Composable
fun LayoutPreview(
    layout: Layout,
    widgets: List<Widget>,
    modifier: Modifier = Modifier
) {
    val aspectRatio = when (layout) {
        Layout.HORIZONTAL -> 16f / 9f
        Layout.VERTICAL -> 9f / 16f
    }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .background(Color.DarkGray)
            .border(2.dp, Color.White)
    ) {
        Text(
            text = "${layout.name} Layout",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(8.dp)
        )

        Text(
            text = "Widgets: ${widgets.size}",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}
