package com.keno.rxplayer.persistence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberLibraryStore(): LibraryStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { LibraryStore(context.filesDir.resolve("library.db").absolutePath) }
}
