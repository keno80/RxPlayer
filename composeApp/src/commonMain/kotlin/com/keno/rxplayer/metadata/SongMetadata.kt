package com.keno.rxplayer.metadata

import com.keno.rxplayer.BilibiliTrack
import com.keno.rxplayer.LyricLine
import kotlinx.serialization.json.*
import kotlin.math.abs

enum class SongProvider(val label: String) { QQ("QQ音乐"), KUGOU("酷狗音乐"), NETEASE("网易云音乐"), LRCLIB("LRCLIB") }
data class SongCandidate(val id: Long, val title: String, val artists: List<String>, val album: String,
    val durationMs: Int, val artworkUrl: String? = null, val provider: SongProvider = SongProvider.NETEASE,
    val providerId: String? = null) {
    val key: String get() = "${provider.name}:${providerId ?: id}"
}
data class SongMatch(val song: SongCandidate, val lyrics: List<LyricLine> = emptyList(), val lyricsSource: String? = null)
data class SongMetadataRecord(val trackId: String, val match: SongMatch? = null, val automaticDisabled: Boolean = false)
data class SongHint(val title: String, val artist: String, val versions: Set<String>,
    val alternateTitle: String? = null, val alternateArtist: String? = null)

internal fun songKey(text: String) = text.lowercase().replace(Regex("[\\s\\p{P}\\p{S}]"), "")
internal fun songBaseTitle(text: String) = text.replace(Regex("[（(].*?[）)]"), "").trim()
internal fun songVersions(text: String): Set<String> = buildSet {
    val value = text.lowercase()
    listOf("live" to "现场|\\blive\\b", "cover" to "翻唱|\\bcover\\b", "instrumental" to "伴奏|纯音乐|\\binstrumental\\b",
        "cantonese" to "粤语|粵語|cantonese", "remix" to "\\bremix\\b|混音版", "medley" to "串烧|串燒|合集|合辑|medley",
        "duet" to "合唱|duet", "acoustic" to "不插电|不插電|acoustic", "speed" to "加速|慢速|升调|降调|sped.?up|slowed",
        "english" to "英语|英語|英文|\\benglish\\b", "japanese" to "日语|日語|日文|\\bjapanese\\b",
        "korean" to "韩语|韓語|韩文|韓文|\\bkorean\\b", "mandarin" to "国语|國語|普通话|普通話|\\bmandarin\\b",
        "remaster" to "重制|重製|重录|重錄|remaster|re.?record", "female" to "女声|女聲|\\bfemale\\b",
        "male" to "男声|男聲|\\bmale\\b", "radio" to "radio.?edit|广播版|廣播版", "extended" to "\\bextended\\b|加长版|加長版").forEach { (key, expression) ->
        if (Regex(expression).containsMatchIn(value)) add(key)
    }
    // An unrecognised edition is not permission to erase its qualifier and assume the original.
    if (isEmpty() && Regex("版|\\b(?:version|edit|mix|ver)\\b").containsMatchIn(value)) add("unknown-edition")
}

/** Conservative textual clues only. A publisher name is deliberately never used as a singer. */
fun songHint(track: BilibiliTrack, description: String = ""): SongHint? {
    val title = if (track.partCount > 1) track.part.title else track.item.title
    val quoted = Regex("[《〈「]([^》〉」]{1,120})[》〉」]").find(title)
    val separated = Regex("^(.{1,60}?)\\s+[-–—]\\s+(.{1,120})$").find(title)
    fun credit(label: String) = Regex("(?m)^\\s*(?:$label)\\s*[:：]\\s*([^\\r\\n|]{1,60})").find(description)?.groupValues?.get(1)?.trim()
    // These labels describe encoding quality, not a singer or a recording edition.
    fun clean(value: String) = value.replace(Regex("[【\\[]\\s*(?:无损音质|无损|高音质|高品质|HQ|FLAC|Hi-?Res|320K(?:bps)?)\\s*[】\\]]", RegexOption.IGNORE_CASE), "").trim()
    val left = separated?.groupValues?.get(1)?.let(::clean)
    val right = separated?.groupValues?.get(2)?.let(::clean)
    val credited = credit("翻唱") ?: credit("演唱|歌手")
    val name: String = (quoted?.groupValues?.get(1) ?:
        (if (credited != null && right != null && songKey(right) == songKey(credited)) left else right)) ?: return null
    val before = quoted?.let { title.substring(0, it.range.first).trim().trimEnd('-', '–', '—', '：', ':') }
        ?.replace(Regex("^\\[[^]]*]\\s*"), "")?.trim()
    val explicit = before?.takeIf { it.length in 1..40 && !Regex("[，。！!｜|…]|我们|现场|翻唱|哭腔|教唱|合集|百万|演唱会").containsMatchIn(it) }
    val artist = credited ?: left ?: explicit
        ?: Regex("是(.{1,40}?)演唱").find(description)?.groupValues?.get(1)?.trim() ?: return null
    val ambiguousOrder = quoted == null && credited == null && left != null && right != null
    return SongHint(songBaseTitle(name), artist, songVersions(title + "\n" + description),
        if (ambiguousOrder) songBaseTitle(left!!) else null, if (ambiguousOrder) right else null)
}

fun autoMatch(hint: SongHint, track: BilibiliTrack, candidates: List<SongCandidate>): SongCandidate? {
    if (hint.versions.any { it in setOf("live", "cover", "medley", "speed", "remaster", "unknown-edition") }) return null
    val originalTitle = if (track.partCount > 1) track.part.title else track.item.title
    if (Regex("[（(]").containsMatchIn(originalTitle)) return null
    val eligible = candidates.filter { song ->
        // Catalog parentheses may be an alias or a recording edition. Only the user can resolve that ambiguity.
        !Regex("[（(]").containsMatchIn(song.title) && hintMatches(hint, song) &&
            songVersions(song.title) == hint.versions && lyricsFit(track, song)
    }
    return SongProvider.entries.firstNotNullOfOrNull { provider -> eligible.filter { it.provider == provider }.singleOrNull() }
}

fun lyricsFit(track: BilibiliTrack, song: SongCandidate, description: String = ""): Boolean {
    val title = if (track.partCount > 1) track.part.title else track.item.title
    val versions = songVersions(title + "\n" + description)
    val hint = songHint(track, description)
    return track.durationMs > 0 && song.durationMs > 0 && abs(track.durationMs.toLong() - song.durationMs) <= 2000 &&
        versions.none { it in setOf("medley", "speed", "unknown-edition") } && versions == songVersions(song.title) &&
        (hint == null || sameSingers(hint.artist, song.artists) || alternateHintMatches(hint, song))
}

private fun hintMatches(hint: SongHint, song: SongCandidate): Boolean =
    (songKey(songBaseTitle(song.title)) == songKey(hint.title) && sameSingers(hint.artist, song.artists)) || alternateHintMatches(hint, song)

private fun alternateHintMatches(hint: SongHint, song: SongCandidate): Boolean =
    hint.alternateTitle != null && hint.alternateArtist != null &&
        songKey(songBaseTitle(song.title)) == songKey(hint.alternateTitle) && sameSingers(hint.alternateArtist, song.artists)

internal fun sameSingers(hint: String, artists: List<String>): Boolean {
    if (artists.size == 1 && songKey(hint) == songKey(artists.single())) return true
    val hinted = hint.split(Regex("[/、,&＆]|\\s+(?:feat|ft)\\.?\\s+", RegexOption.IGNORE_CASE)).map(::songKey).filter { it.isNotEmpty() }.toSet()
    return hinted == artists.map(::songKey).toSet()
}

fun parseLrc(text: String): List<LyricLine> {
    if (text.length > 262144) return emptyList()
    val offset = Regex("(?im)^\\[offset:([+-]?\\d{1,9})]").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val timestamp = Regex("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]")
    return text.lineSequence().take(5000).flatMap { line ->
        val marks = timestamp.findAll(line).toList()
        val words = line.replace(timestamp, "").trim()
        if (!line.startsWith("[") || words.isEmpty() || marks.isEmpty()) emptySequence() else marks.asSequence().mapNotNull { mark ->
            val at = mark.groupValues[1].toLong() * 60000 + mark.groupValues[2].toLong() * 1000 +
                mark.groupValues[3].padEnd(3, '0').toLong() + offset
            at.coerceAtLeast(0).takeIf { it <= Int.MAX_VALUE }?.let { LyricLine(it.toInt(), words) }
        }
    }.distinct().sortedBy { it.atMs }.toList()
}

internal fun encodeSongRecord(record: SongMetadataRecord): String = buildJsonObject {
    put("disabled", record.automaticDisabled)
    record.match?.let { match ->
        val song = match.song
        put("id", song.id); put("title", song.title); put("album", song.album); put("duration", song.durationMs)
        put("provider", song.provider.name); song.providerId?.let { put("providerId", it) }
        put("artists", JsonArray(song.artists.map(::JsonPrimitive)))
        song.artworkUrl?.let { put("cover", it) }; match.lyricsSource?.let { put("lyricsSource", it) }
        put("lyrics", JsonArray(match.lyrics.map { line -> buildJsonObject { put("at", line.atMs); put("text", line.text) } }))
    }
}.toString()

internal fun decodeSongRecord(trackId: String, value: String): SongMetadataRecord {
    val data = Json.parseToJsonElement(value).jsonObject
    val song = data["id"]?.jsonPrimitive?.long?.let { id ->
        SongCandidate(id, data.getValue("title").jsonPrimitive.content, data.getValue("artists").jsonArray.map { it.jsonPrimitive.content },
            data.getValue("album").jsonPrimitive.content, data.getValue("duration").jsonPrimitive.int, data["cover"]?.jsonPrimitive?.content,
            data["provider"]?.jsonPrimitive?.content?.let(SongProvider::valueOf) ?: SongProvider.NETEASE,
            data["providerId"]?.jsonPrimitive?.content)
    }
    val match = song?.let { SongMatch(it, data.getValue("lyrics").jsonArray.map { line ->
        val row = line.jsonObject; LyricLine(row.getValue("at").jsonPrimitive.int, row.getValue("text").jsonPrimitive.content)
    }, data["lyricsSource"]?.jsonPrimitive?.content) }
    return SongMetadataRecord(trackId, match, data.getValue("disabled").jsonPrimitive.boolean)
}
