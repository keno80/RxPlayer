package com.keno.rxplayer

enum class MusicSource(val label: String) {
    Netease("网易云"), Bilibili("Bilibili"), YouTube("YouTube"),
}

data class DemoTrack(
    val id: String,
    val title: String,
    val subtitle: String,
    val source: MusicSource,
    val artwork: Int,
    val artist: String = "RxPlayer Studio",
    val lyrics: List<LyricLine> = emptyList(),
) {
    val resourcePath: String get() = "files/$id.wav"
}

data class LyricLine(val atMs: Int, val text: String)

// Authored layout fixtures for bundled instrumental demos, never returned as online lyrics.
private val nightLyrics = listOf(
    LyricLine(0, "风把街灯轻轻点亮"), LyricLine(3_000, "沿着夜色慢慢启航"),
    LyricLine(6_000, "把喧嚣留在远方"), LyricLine(9_000, "让旋律陪我去流浪"),
    LyricLine(12_000, "下一站有温柔的光"), LyricLine(15_000, "我们迎着清晨歌唱"),
)

val demoTracks = listOf(
    DemoTrack("night", "夜航", "给城市留一盏灯", MusicSource.Bilibili, 0, lyrics = nightLyrics),
    DemoTrack("daylight", "日光之间", "把午后调成慢速", MusicSource.Netease, 1),
    DemoTrack("orbit", "环游", "沿着声音出发", MusicSource.YouTube, 2),
)
