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
) {
    val resourcePath: String get() = "files/$id.wav"
}

val demoTracks = listOf(
    DemoTrack("night", "夜航", "给城市留一盏灯", MusicSource.Bilibili, 0),
    DemoTrack("daylight", "日光之间", "把午后调成慢速", MusicSource.Netease, 1),
    DemoTrack("orbit", "环游", "沿着声音出发", MusicSource.YouTube, 2),
)
