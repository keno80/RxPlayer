package com.keno.rxplayer.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

internal actual fun decodeCover(bytes: ByteArray): ImageBitmap {
    Data.makeFromBytes(bytes).use { data -> Codec.makeFromData(data).use { codec ->
        require(codec.width in 1..4096 && codec.height in 1..4096 && codec.width.toLong() * codec.height <= 4_194_304)
    } }
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
