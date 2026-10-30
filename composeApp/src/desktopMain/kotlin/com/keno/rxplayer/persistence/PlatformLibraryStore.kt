package com.keno.rxplayer.persistence

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

@Composable
actual fun rememberLibraryStore(): LibraryStore = remember {
    val directory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "RxPlayer")
    LibraryStore(File(directory, "library.db").absolutePath) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create application data directory" }
    }
}
