package com.keno.rxplayer.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object UiSpacing {
    val compactInset = 16.dp
    val panelInset = 24.dp
    val sectionGap = 24.dp
    val paneGap = 24.dp
    val contentGap = 12.dp
    val cardInset = 16.dp

    fun pageInset(windowWidth: Dp): Dp =
        if (windowWidth < 600.dp) compactInset else panelInset
}
