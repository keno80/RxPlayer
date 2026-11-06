// Google Material Symbols Rounded drag_handle, Apache-2.0.
// Source: https://github.com/google/material-design-icons/blob/master/symbols/web/drag_handle/materialsymbolsrounded/drag_handle_24px.svg
// License: design/prototype-v1/assets/LICENSE-Material-Symbols.txt
package com.keno.rxplayer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal val QueueDragHandle: ImageVector by lazy {
    ImageVector.Builder("QueueDragHandle", 24.dp, 24.dp, 960f, 960f)
        .addPath(PathParser().parsePathString("M200 600q-17 0-28.5-11.5T160 560q0-17 11.5-28.5T200 520h560q17 0 28.5 11.5T800 560q0 17-11.5 28.5T760 600H200Zm0-160q-17 0-28.5-11.5T160 400q0-17 11.5-28.5T200 360h560q17 0 28.5 11.5T800 400q0 17-11.5 28.5T760 440H200Z").toNodes(), fill = SolidColor(Color.Black))
        .build()
}
