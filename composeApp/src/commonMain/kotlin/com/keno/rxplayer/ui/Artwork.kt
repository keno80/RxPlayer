package com.keno.rxplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.keno.rxplayer.generated.resources.*
import org.jetbrains.compose.resources.painterResource

/** Rasterized from the approved original SVGs; online artwork is a separate source capability. */
@Composable
fun Artwork(index: Int, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp)) {
    val resource = when (index) {
        1 -> Res.drawable.sea_letter
        2 -> Res.drawable.sunset_session
        else -> Res.drawable.night_voyage
    }
    Image(painterResource(resource), contentDescription = null, modifier = modifier.clip(shape), contentScale = ContentScale.Crop)
}
