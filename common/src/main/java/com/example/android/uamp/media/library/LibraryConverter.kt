package com.example.android.uamp.media.library

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts the website's `/latestdata` library (`songs`, `playlists`) into the catalog JSON
 * ([JsonCatalog]: `music`, `playlists`) that [JsonSource] parses. Each song gets a stream URL
 * from the same redirect service the web player uses.
 */
internal object LibraryConverter {

    private const val STREAM_BASE = "https://home.kaiser.lol:59436"

    fun toCatalogJson(latestData: String): String {
        val root = JSONObject(latestData)
        val songs = root.optJSONArray("songs") ?: JSONArray()
        val music = JSONArray()
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val id = song.optString("id")
            val source = streamUrl(id) ?: continue
            music.put(
                JSONObject()
                    .put("id", id)
                    .put("title", song.optString("title"))
                    .put("album", song.optString("album").ifEmpty { "Unknown Album" })
                    .put("artist", song.optString("artist").ifEmpty { "Unknown Artist" })
                    .put("genre", "Unknown")
                    .put("source", source)
                    .put("image", song.optString("cover"))
                    .put("trackNumber", 1)
                    .put("totalTrackCount", 100)
                    .put("duration", song.optDouble("duration", -1.0))
            )
        }
        return JSONObject()
            .put("music", music)
            .put("playlists", playlists(root.optJSONArray("playlists"), addedTimes(songs)))
            .toString()
    }

    /** Keeps only the fields the app reads, coercing types so odd values can't break parsing. */
    private fun playlists(source: JSONArray?, addedAt: Map<String, Double>): JSONArray {
        val result = JSONArray()
        if (source == null) return result
        for (i in 0 until source.length()) {
            val playlist = source.optJSONObject(i) ?: continue
            val ids = JSONArray()
            val songs = playlist.optJSONArray("songs")
            if (songs != null) {
                for (j in 0 until songs.length()) {
                    val entry = songs.opt(j)
                    val id = if (entry is JSONObject) entry.optString("id") else entry?.toString().orEmpty()
                    if (id.isNotEmpty() && id != "null") ids.put(id)
                }
            }
            val title = playlist.optString("title")
            // The server keeps the Library in the order songs were added (oldest first);
            // the app presents it newest first.
            val ordered = if (title == "Library") newestFirst(ids, addedAt) else ids
            result.put(
                JSONObject()
                    .put("title", title)
                    .put("createdAt", playlist.optDouble("createdAt", 0.0).toLong())
                    .put("updatedAt", playlist.optDouble("updatedAt", 0.0).toLong())
                    .put("songs", ordered)
            )
        }
        return result
    }

    private fun addedTimes(songs: JSONArray): Map<String, Double> {
        val times = HashMap<String, Double>()
        for (i in 0 until songs.length()) {
            val song = songs.optJSONObject(i) ?: continue
            val created = song.optDouble("createdAt", Double.NaN)
            if (!created.isNaN()) times[song.optString("id")] = created
        }
        return times
    }

    /** Sorts by added time when known (stable for ties), otherwise reverses the stored order. */
    private fun newestFirst(ids: JSONArray, addedAt: Map<String, Double>): JSONArray {
        val list = (0 until ids.length()).map { ids.getString(it) }
        val sorted = if (list.count { it in addedAt } > list.size / 2) {
            list.withIndex()
                .sortedWith(compareByDescending<IndexedValue<String>> { addedAt[it.value] ?: 0.0 }.thenByDescending { it.index })
                .map { it.value }
        } else {
            list.reversed()
        }
        return JSONArray(sorted)
    }

    /** Short summary of a raw library for diagnostics. */
    fun describe(latestData: String): String = try {
        val songs = JSONObject(latestData).optJSONArray("songs")
        val first = songs?.optJSONObject(0)
        "songs=${songs?.length()}, first id=${first?.optString("id")}, keys=${first?.keys()?.asSequence()?.take(8)?.toList()}"
    } catch (e: Exception) {
        "unreadable: $e"
    }

    private fun streamUrl(id: String): String? = when {
        id.startsWith("y_") -> "$STREAM_BASE/youtuberedirect?id=${id.substring(2)}"
        id.startsWith("s_") || id.startsWith("a_") -> "$STREAM_BASE/soundcloudredirect?id=${id.substring(2)}"
        else -> null
    }
}
