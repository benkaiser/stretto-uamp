/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.uamp.media.library

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Source of [MediaMetadataCompat] objects created from a basic JSON stream.
 *
 * The definition of the JSON is specified in the docs of [JsonMusic] in this file,
 * which is the object representation of it.
 */
internal class JsonSource(private val source: Uri, private val context: Context) : AbstractMusicSource() {

    companion object {
        const val ORIGINAL_ARTWORK_URI_KEY = "com.example.android.uamp.JSON_ARTWORK_URI"
        private const val CACHE_FILE_NAME = "catalog.json"
        private const val TAG = "JsonSource"
    }

    private var catalog: List<androidx.media3.common.MediaItem> = emptyList()
    private var playlists: List<JsonPlaylist> = emptyList()

    /** Callback invoked when the catalog is updated from a background network fetch. */
    var onCatalogUpdated: (() -> Unit)? = null

    /** Track the raw JSON string for comparison to detect changes. */
    private var lastCatalogJson: String? = null

    /** The cache file in the app's internal cache directory. */
    private val cacheFile get() = File(context.cacheDir, CACHE_FILE_NAME)

    init {
        // Try loading from cache synchronously during construction so that
        // STATE_INITIALIZED is set before any MediaBrowser can connect and call
        // onGetChildren(). This avoids a race where the load() coroutine is
        // queued on Dispatchers.Main but can't run until after the browser
        // connection chain finishes — causing the UI to block on ConditionVariable.
        val cachedJson = loadFromCache()
        if (cachedJson != null) {
            val parsed = parseCatalog(cachedJson)
            if (parsed != null) {
                lastCatalogJson = cachedJson
                catalog = parsed.first
                playlists = parsed.second
                state = STATE_INITIALIZED  // Unblocks all waiters immediately
                Log.i(TAG, "Loaded catalog from cache (${catalog.size} items)")
            } else {
                Log.w(TAG, "Cached catalog was corrupt, deleting")
                try { cacheFile.delete() } catch (_: Exception) {}
                state = STATE_INITIALIZING
            }
        } else {
            state = STATE_INITIALIZING
        }
    }

    override fun iterator(): Iterator<MediaItem> = catalog.iterator()

    override suspend fun load() {
        // The cache was already loaded synchronously in init{}.
        // This method only does the network revalidation.
        val freshJson = downloadJsonString(source)
        if (freshJson != null) {
            if (freshJson != lastCatalogJson) {
                // Data changed — update catalog
                val parsed = parseCatalog(freshJson)
                if (parsed != null) {
                    lastCatalogJson = freshJson
                    saveToCache(freshJson)
                    catalog = parsed.first
                    playlists = parsed.second
                    if (state == STATE_INITIALIZED) {
                        // Already was initialized from cache — notify of update
                        onCatalogUpdated?.invoke()
                    } else {
                        // First successful load (no cache existed)
                        state = STATE_INITIALIZED
                    }
                }
            } else {
                // Network data matches cache — save to refresh file timestamp
                saveToCache(freshJson)
            }
        } else if (state != STATE_INITIALIZED) {
            // Network failed and no cache was available
            catalog = emptyList()
            state = STATE_ERROR
        }
        // If network failed but cache was loaded, just keep using the cache (no error)
    }

    /**
     * Parses a raw JSON string into a list of MediaItems and playlists.
     * Returns null if parsing fails.
     */
    private fun parseCatalog(json: String): Pair<List<MediaItem>, List<JsonPlaylist>>? {
        return try {
            val musicCat = Gson().fromJson(json, JsonCatalog::class.java)
            // Get the base URI to fix up relative references later.
            val baseUri = source.toString().removeSuffix(source.lastPathSegment ?: "")

            val mediaItems = musicCat.music.map { song ->
                // The JSON may have paths that are relative to the source of the JSON
                // itself. We need to fix them up here to turn them into absolute paths.
                source.scheme?.let { scheme ->
                    if (!song.source.startsWith(scheme)) {
                        song.source = baseUri + song.source
                    }
                    if (!song.image.startsWith(scheme)) {
                        song.image = baseUri + song.image
                    }
                }

                val jsonImageUri = Uri.parse(song.image)
                val imageUri = AlbumArtContentProvider.mapUri(jsonImageUri)
                val mediaMetadata = MediaMetadata.Builder()
                    .from(song)
                    .apply {
                        setArtworkUri(imageUri) // Used by ExoPlayer and Notification
                        // Keep the original artwork URI for being included in Cast metadata object.
                        val extras = Bundle()
                        extras.putString(ORIGINAL_ARTWORK_URI_KEY, jsonImageUri.toString())
                        setExtras(extras)
                    }
                    .build()
                MediaItem.Builder()
                    .apply {
                        setMediaId(song.id)
                        setUri(song.source)
                        setMimeType(MimeTypes.AUDIO_MPEG)
                        setMediaMetadata(mediaMetadata)
                    }.build()
            }.toList()

            Pair(mediaItems, musicCat.playlists)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing catalog JSON", e)
            null
        }
    }

    /**
     * Downloads the raw JSON string from the given URI.
     * Returns null if the download fails.
     */
    private suspend fun downloadJsonString(catalogUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val catalogConn = URL(catalogUri.toString())
                val reader = BufferedReader(InputStreamReader(catalogConn.openStream()))
                reader.use { it.readText() }
            } catch (ioException: IOException) {
                Log.e(TAG, "Error downloading catalog", ioException)
                null
            }
        }
    }

    /**
     * Loads the raw JSON string from the disk cache.
     * Returns null if the cache file doesn't exist or can't be read.
     */
    private fun loadFromCache(): String? {
        return try {
            val file = cacheFile
            if (file.exists()) {
                file.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading cache file", e)
            null
        }
    }

    /**
     * Saves the raw JSON string to the disk cache.
     */
    private fun saveToCache(json: String) {
        try {
            cacheFile.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing cache file", e)
        }
    }

    override fun getPlaylists(): List<JsonPlaylist> {
        return playlists
    }

    override fun getItemFromPlaylist(playlist: JsonPlaylist): MediaItem? {
        val mediaItem = catalog.find { item ->
            playlist.songs.contains(item.mediaId)
        }
        return mediaItem
    }
}

/**
 * Extension method for [MediaMetadataCompat.Builder] to set the fields from
 * our JSON constructed object (to make the code a bit easier to see).
 */
fun MediaMetadata.Builder.from(jsonMusic: JsonMusic): MediaMetadata.Builder {
    setTitle(jsonMusic.title)
    setDisplayTitle(jsonMusic.title)
    setArtist(jsonMusic.artist)
    setAlbumTitle(jsonMusic.album)
    setGenre(jsonMusic.genre)
    setArtworkUri(Uri.parse(jsonMusic.image))
    setTrackNumber(jsonMusic.trackNumber.toInt())
    setTotalTrackCount(jsonMusic.totalTrackCount.toInt())
    setFolderType(MediaMetadata.FOLDER_TYPE_NONE)
    setIsPlayable(true)
    // The duration from the JSON is given in seconds, but the rest of the code works in
    // milliseconds. Here's where we convert to the proper units.
    val durationMs = TimeUnit.SECONDS.toMillis(jsonMusic.duration.toLong())
    val bundle = Bundle()
    bundle.putLong("durationMs", durationMs)
    return this
}

/**
 * Wrapper object for our JSON in order to be processed easily by GSON.
 */
class JsonCatalog {
    var music: List<JsonMusic> = ArrayList()
    var playlists: List<JsonPlaylist> = ArrayList()
}

/**
 * An individual piece of music included in our JSON catalog.
 * The format from the server is as specified:
 * ```
 *     { "music" : [
 *     { "title" : // Title of the piece of music
 *     "album" : // Album title of the piece of music
 *     "artist" : // Artist of the piece of music
 *     "genre" : // Primary genre of the music
 *     "source" : // Path to the music, which may be relative
 *     "image" : // Path to the art for the music, which may be relative
 *     "trackNumber" : // Track number
 *     "totalTrackCount" : // Track count
 *     "duration" : // Duration of the music in seconds
 *     "site" : // Source of the music, if applicable
 *     }
 *     ]}
 * ```
 *
 * `source` and `image` can be provided in either relative or
 * absolute paths. For example:
 * ``
 *     "source" : "https://www.example.com/music/ode_to_joy.mp3",
 *     "image" : "ode_to_joy.jpg"
 * ``
 *
 * The `source` specifies the full URI to download the piece of music from, but
 * `image` will be fetched relative to the path of the JSON file itself. This means
 * that if the JSON was at "https://www.example.com/json/music.json" then the image would be found
 * at "https://www.example.com/json/ode_to_joy.jpg".
 */
@Suppress("unused")
class JsonMusic {
    var id: String = ""
    var title: String = ""
    var album: String = ""
    var artist: String = ""
    var genre: String = ""
    var source: String = ""
    var image: String = ""
    var trackNumber: Long = 0
    var totalTrackCount: Long = 0
    var duration: Float = -1f
    var site: String = ""
}
