/*
 * Copyright 2019 Google Inc. All rights reserved.
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
import com.example.android.uamp.media.MusicService
import com.example.android.uamp.media.PersistentStorage
import com.example.android.uamp.media.R
import com.example.android.uamp.media.extensions.urlEncoded

/**
 * Represents a tree of media that's used by [MusicService.onLoadChildren].
 *
 * [BrowseTree] maps a media id (see: [MediaMetadataCompat.METADATA_KEY_MEDIA_ID]) to one (or
 * more) [MediaMetadataCompat] objects, which are children of that media id.
 *
 * For example, given the following conceptual tree:
 * root
 *  +-- Albums
 *  |    +-- Album_A
 *  |    |    +-- Song_1
 *  |    |    +-- Song_2
 *  ...
 *  +-- Artists
 *  ...
 *
 *  Requesting `browseTree["root"]` would return a list that included "Albums", "Artists", and
 *  any other direct children. Taking the media ID of "Albums" ("Albums" in this example),
 *  `browseTree["Albums"]` would return a single item list "Album_A", and, finally,
 *  `browseTree["Album_A"]` would return "Song_1" and "Song_2". Since those are leaf nodes,
 *  requesting `browseTree["Song_1"]` would return null (there aren't any children of it).
 */
internal class BrowseTree(
    val context: Context,
    val musicSource: MusicSource,
    val recentMediaId: String? = null,
    private val storage: PersistentStorage? = null
) {
    private val mediaIdToChildren = mutableMapOf<String, MutableList<MediaItem>>()
    private val mediaIdToMediaItem = mutableMapOf<String, MediaItem>()

    /**
     * Whether to allow clients which are unknown (not on the allowed list) to use search on this
     * [BrowseTree].
     */
    val searchableByUnknownCaller = true

    /**
     * In this example, there's a single root node (identified by the constant
     * [UAMP_BROWSABLE_ROOT]). The root's children are each album included in the
     * [MusicSource], and the children of each album are the songs on that album.
     * (See [BrowseTree.buildAlbumRoot] for more details.)
     *
     * TODO: Expand to allow more browsing types.
     */
    init {
        val rootList = mediaIdToChildren[UAMP_BROWSABLE_ROOT] ?: mutableListOf()

        val recommendedCategory = MediaMetadata.Builder().apply {
            setTitle(context.getString(R.string.recommended_title))
            setArtworkUri(
                Uri.parse(
                    RESOURCE_ROOT_URI +
                            context.resources.getResourceEntryName(R.drawable.ic_recommended)
                )
            )
            setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
            setIsPlayable(false)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_RECOMMENDED_ROOT)
            setMediaMetadata(recommendedCategory)
        }.build()

        val albumsMetadata = MediaMetadata.Builder().apply {
            setTitle(context.getString(R.string.albums_title))
            setArtworkUri(
                Uri.parse(
                    RESOURCE_ROOT_URI +
                            context.resources.getResourceEntryName(R.drawable.ic_album)
                )
            )
            setIsPlayable(false)
            setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_ALBUMS_ROOT)
            setMediaMetadata(albumsMetadata)
        }.build()

        // Playlists
        val playlistsMetadata = MediaMetadata.Builder().apply {
            setTitle(context.getString(R.string.playlists_title))
            setArtworkUri(
                Uri.parse(
                    RESOURCE_ROOT_URI +
                            context.resources.getResourceEntryName(R.drawable.ic_album)
                )
            )
            setIsPlayable(false)
            setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_PLAYLISTS_ROOT)
            setMediaMetadata(playlistsMetadata)
        }.build()

        val playlistRoot = buildPlaylistRoot()

        // Build playlist items and their children (but don't add to root yet - will be sorted on-demand)
        musicSource.getPlaylists().forEach { playlist ->
            val mediaItem = musicSource.getItemFromPlaylist(playlist)
            if (mediaItem === null) {
                return@forEach
            }
            // now collect all the songs under the playlist title as a parent
            val playlistChildren = mutableListOf<MediaItem>()
            playlist.songs.forEach { songId ->
                val song = musicSource.find { it.mediaId == songId }
                if (song === null) {
                    return@forEach
                }
                playlistChildren += song
            }
            val playlistMetadata = mediaItem.mediaMetadata.buildUpon().apply {
                setTitle(playlist.title)
                setAlbumTitle("")
                setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                setIsPlayable(false)
            }.build().withSongCount(playlistChildren.size)
            val playlistMediaItem = mediaItem.buildUpon().apply {
                setMediaId(playlist.title.toString().urlEncoded)
                setMediaMetadata(playlistMetadata)
            }.build()
            playlistRoot += playlistMediaItem
            mediaIdToChildren[playlistMediaItem.mediaId] = playlistChildren
            // Insert shuffle item at top of playlist
            val playlistShuffleItem = buildShuffleItem(playlistMediaItem.mediaId)
            playlistChildren.add(0, playlistShuffleItem)
            mediaIdToMediaItem[playlistShuffleItem.mediaId] = playlistShuffleItem
        }

        mediaIdToChildren[UAMP_BROWSABLE_ROOT] = rootList
        musicSource.forEach { mediaItem ->
            val albumMediaId = mediaItem.mediaMetadata.albumTitle.toString().urlEncoded
            val albumChildren = mediaIdToChildren[albumMediaId] ?: buildAlbumRoot(mediaItem)
            albumChildren += mediaItem

            Log.d("BrowseTree", "loading catalogue for " + mediaItem.mediaId)
            // Add the first track of each album to the 'Recommended' category
            if (mediaItem.mediaMetadata.trackNumber == 1) {
                val recommendedChildren = mediaIdToChildren[UAMP_RECOMMENDED_ROOT]
                    ?: mutableListOf()
                recommendedChildren += mediaItem
                mediaIdToChildren[UAMP_RECOMMENDED_ROOT] = recommendedChildren
            }

            // If this was recently played, add it to the recent root.
            if (mediaItem.mediaId == recentMediaId) {
                mediaIdToChildren[UAMP_RECENT_ROOT] = mutableListOf(mediaItem)
            }
            mediaIdToMediaItem[mediaItem.mediaId] = mediaItem
        }

        buildArtistsAndSongs(rootList)
        finalizeAlbums()

        // Insert shuffle item at top of the recommended (Library) root
        val recommendedChildren = mediaIdToChildren[UAMP_RECOMMENDED_ROOT]
        if (recommendedChildren != null && recommendedChildren.isNotEmpty()) {
            val libraryShuffleItem = buildShuffleItem(UAMP_RECOMMENDED_ROOT)
            recommendedChildren.add(0, libraryShuffleItem)
            mediaIdToMediaItem[libraryShuffleItem.mediaId] = libraryShuffleItem
        }
    }

    /**
     * Provides access to the list of children with the `get` operator.
     * i.e.: `browseTree\[UAMP_BROWSABLE_ROOT\]`
     *
     * For playlists, this returns them sorted by Most Recently Used (MRU) order.
     */
    operator fun get(mediaId: String): List<MediaItem>? {
        val children = mediaIdToChildren[mediaId] ?: return null

        // If requesting playlists, sort them by MRU order and expose when each was last played.
        if (mediaId == UAMP_PLAYLISTS_ROOT) {
            return children.map { playlistItem ->
                val accessTime = storage?.getPlaylistAccessTime(playlistItem.mediaId) ?: 0L
                val extras = Bundle(playlistItem.mediaMetadata.extras ?: Bundle())
                extras.putLong(ACCESS_TIME_KEY, accessTime)
                playlistItem.buildUpon()
                    .setMediaMetadata(
                        playlistItem.mediaMetadata.buildUpon().setExtras(extras).build()
                    )
                    .build() to accessTime
            }.sortedByDescending { it.second }.map { it.first }
        }

        return children
    }

    /** Provides access to the media items by media id. */
    fun getMediaItemByMediaId(mediaId: String) = mediaIdToMediaItem[mediaId]

    /** Adds the Artists root, per-artist nodes (with shuffle items) and the hidden all-songs node. */
    private fun buildArtistsAndSongs(rootList: MutableList<MediaItem>) {
        val allSongs = mutableListOf<MediaItem>()
        val byArtist = linkedMapOf<String, MutableList<MediaItem>>()
        musicSource.forEach { song ->
            allSongs += song
            val artist = song.mediaMetadata.artist?.toString()?.trim().orEmpty()
                .ifEmpty { UNKNOWN_ARTIST }
            byArtist.getOrPut(artist) { mutableListOf() } += song
        }
        mediaIdToChildren[UAMP_SONGS_ROOT] = allSongs

        val artistItems = byArtist.entries
            .sortedBy { it.key.lowercase() }
            .map { (artist, songs) ->
                val artistId = "$UAMP_ARTIST_PREFIX${artist.urlEncoded}"
                val metadata = MediaMetadata.Builder().apply {
                    setTitle(artist)
                    setArtworkUri(songs.first().mediaMetadata.artworkUri)
                    setFolderType(MediaMetadata.FOLDER_TYPE_ARTISTS)
                    setIsPlayable(false)
                    setExtras(songs.first().mediaMetadata.extras)
                }.build().withSongCount(songs.size)
                val shuffleItem = buildShuffleItem(artistId)
                mediaIdToChildren[artistId] = (listOf(shuffleItem) + songs).toMutableList()
                mediaIdToMediaItem[shuffleItem.mediaId] = shuffleItem
                MediaItem.Builder().setMediaId(artistId).setMediaMetadata(metadata).build()
            }.toMutableList()
        mediaIdToChildren[UAMP_ARTISTS_ROOT] = artistItems

        val artistsMetadata = MediaMetadata.Builder().apply {
            setTitle(context.getString(R.string.artists_title))
            setArtworkUri(
                Uri.parse(
                    RESOURCE_ROOT_URI +
                            context.resources.getResourceEntryName(R.drawable.ic_album)
                )
            )
            setIsPlayable(false)
            setFolderType(MediaMetadata.FOLDER_TYPE_ARTISTS)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_ARTISTS_ROOT)
            setMediaMetadata(artistsMetadata)
        }.build()
    }

    /** Sorts albums alphabetically and records how many songs each one holds. */
    private fun finalizeAlbums() {
        val albums = mediaIdToChildren[UAMP_ALBUMS_ROOT] ?: return
        val withCounts = albums.map { album ->
            val count = mediaIdToChildren[album.mediaId]?.size ?: 0
            album.buildUpon()
                .setMediaMetadata(album.mediaMetadata.withSongCount(count))
                .build()
        }.sortedBy { it.mediaMetadata.albumTitle?.toString()?.lowercase() ?: "" }
        mediaIdToChildren[UAMP_ALBUMS_ROOT] = withCounts.toMutableList()
    }

    private fun MediaMetadata.withSongCount(count: Int): MediaMetadata {
        val newExtras = Bundle(extras ?: Bundle())
        newExtras.putInt(SONG_COUNT_KEY, count)
        return buildUpon().setExtras(newExtras).build()
    }

    /**
     * Resolves the ordered queue for a play request on [parentId] (a playlist, album, artist or
     * the whole library) along with the index to start from.
     */
    fun getPlayQueue(parentId: String, startMediaId: String?): Pair<MutableList<MediaItem>, Int> {
        val queue = playableChildren(parentId)
        val index = startMediaId?.let { id -> queue.indexOfFirst { it.mediaId == id } } ?: -1
        return Pair(queue, if (index >= 0) index else 0)
    }

    private fun playableChildren(parentId: String): MutableList<MediaItem> {
        if (parentId == UAMP_RECOMMENDED_ROOT || parentId == UAMP_SONGS_ROOT) {
            return getLibrary()
        }
        val children = mediaIdToChildren[parentId] ?: return getLibrary()
        return children.filter {
            it.mediaMetadata.isPlayable == true && !it.mediaId.startsWith(UAMP_SHUFFLE_PREFIX)
        }.toMutableList()
    }

    /** Every song in the catalog, in catalog order. */
    fun getLibrary(): MutableList<MediaItem> {
        return mediaIdToChildren[UAMP_SONGS_ROOT]?.toMutableList() ?: mutableListOf()
    }

    fun getPlaylist(parentId: String?): MutableList<MediaItem>? {
        if (parentId === null) {
            return null
        }
        return mediaIdToChildren[parentId]?.toMutableList() ?: null
    }

    fun getLibraryShuffledOn(mediaId: String, parentId: String?): MutableList<MediaItem> {
        val libraryOrPlaylist = getPlaylist(parentId) ?: getLibrary()
        // Filter out shuffle items from the queue
        libraryOrPlaylist.removeAll { it.mediaId.startsWith(UAMP_SHUFFLE_PREFIX) }
        val currentSong = getMediaItemByMediaId(mediaId)
        if (currentSong === null) {
            return libraryOrPlaylist
        }
        libraryOrPlaylist.remove(currentSong)
        libraryOrPlaylist.shuffle()
        libraryOrPlaylist.add(0, currentSong)
        return libraryOrPlaylist
    }

    fun getFirstPlayableMediaItem(): MediaItem? {
        return mediaIdToChildren[UAMP_RECOMMENDED_ROOT]?.find {
            it.mediaMetadata.isPlayable == true && !it.mediaId.startsWith(UAMP_SHUFFLE_PREFIX)
        }
    }

    /**
     * Returns all playable songs for a given parent ID, fully shuffled.
     * Filters out non-playable items (e.g., the shuffle item itself).
     */
    fun getShuffledPlaylistOrLibrary(parentId: String): MutableList<MediaItem> {
        val playable = playableChildren(parentId)
        playable.shuffle()
        return playable
    }

    /**
     * Finds the parent ID that contains a song with the given media ID.
     * Prioritizes [preferredParentId] if it contains the song, otherwise searches
     * playlists, then falls back to the recommended root.
     */
    fun findParentForSong(mediaId: String, preferredParentId: String? = null): String? {
        // First check the preferred parent
        if (preferredParentId != null) {
            val children = mediaIdToChildren[preferredParentId]
            if (children != null && children.any { it.mediaId == mediaId }) {
                return preferredParentId
            }
        }

        // Search playlists
        val playlistItems = mediaIdToChildren[UAMP_PLAYLISTS_ROOT]
        if (playlistItems != null) {
            for (playlist in playlistItems) {
                val children = mediaIdToChildren[playlist.mediaId]
                if (children != null && children.any { it.mediaId == mediaId }) {
                    return playlist.mediaId
                }
            }
        }

        // Check albums
        val albumItems = mediaIdToChildren[UAMP_ALBUMS_ROOT]
        if (albumItems != null) {
            for (album in albumItems) {
                val children = mediaIdToChildren[album.mediaId]
                if (children != null && children.any { it.mediaId == mediaId }) {
                    return album.mediaId
                }
            }
        }

        // Fall back to recommended root if it contains the song
        val recommended = mediaIdToChildren[UAMP_RECOMMENDED_ROOT]
        if (recommended != null && recommended.any { it.mediaId == mediaId }) {
            return UAMP_RECOMMENDED_ROOT
        }

        return null
    }

    /**
     * Builds a playable "Shuffle All" MediaItem for the given parent ID.
     */
    private fun buildShuffleItem(parentId: String): MediaItem {
        val shuffleMetadata = MediaMetadata.Builder().apply {
            setTitle(context.getString(R.string.shuffle_all))
            setArtworkUri(
                Uri.parse(
                    RESOURCE_ROOT_URI +
                            context.resources.getResourceEntryName(R.drawable.ic_shuffle)
                )
            )
            setFolderType(MediaMetadata.FOLDER_TYPE_NONE)
            setIsPlayable(true)
        }.build()
        return MediaItem.Builder().apply {
            setMediaId("$UAMP_SHUFFLE_PREFIX$parentId")
            setMediaMetadata(shuffleMetadata)
        }.build()
    }

    /**
     * Builds a node, under the root, that represents an album, given
     * a [MediaItem] object that's one of the songs on that album,
     * marking the item as [MediaMetadata.FOLDER_TYPE_ALBUMS], since it will have child
     * node(s) AKA at least 1 song.
     */
    private fun buildAlbumRoot(mediaItem: MediaItem): MutableList<MediaItem> {
        // Get or create the albums Category list.
        val rootList = mediaIdToChildren[UAMP_ALBUMS_ROOT] ?: mutableListOf()
        // Create the album and add it to the 'Albums' category.
        val albumMetadata = mediaItem.mediaMetadata.buildUpon().apply {
            setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
            setIsPlayable(false)
        }.build()
        val albumMediaItem= mediaItem.buildUpon().apply {
            setMediaId(albumMetadata.albumTitle.toString().urlEncoded)
            setMediaMetadata(albumMetadata)
        }.build()
        rootList += albumMediaItem
        // Set the album root list for look up later,
        mediaIdToChildren[UAMP_ALBUMS_ROOT] = rootList
        // Insert the album's root with an empty list for its children, and return the list.
        return mutableListOf<MediaItem>().also {
            mediaIdToChildren[albumMediaItem.mediaId] = it
        }
    }

    private fun buildPlaylistRoot(): MutableList<MediaItem> {
        mediaIdToChildren[UAMP_PLAYLISTS_ROOT] = mutableListOf()
        return mediaIdToChildren[UAMP_PLAYLISTS_ROOT]!!
    }
}

const val UAMP_BROWSABLE_ROOT = "/"
const val UAMP_EMPTY_ROOT = "@empty@"
const val UAMP_RECOMMENDED_ROOT = "__RECOMMENDED__"
const val UAMP_ALBUMS_ROOT = "__ALBUMS__"
const val UAMP_RECENT_ROOT = "__RECENT__"
const val UAMP_PLAYLISTS_ROOT = "__PLAYLISTS__"
const val UAMP_ARTISTS_ROOT = "__ARTISTS__"
const val UAMP_SONGS_ROOT = "__SONGS__"
const val UAMP_ARTIST_PREFIX = "__ARTIST__/"
const val UAMP_SHUFFLE_PREFIX = "__SHUFFLE__/"

/** Virtual item id of the form `__PLAY__/<parentId>` or `__PLAY__/<parentId>|<startSongId>`. */
const val UAMP_PLAY_PREFIX = "__PLAY__/"
const val UAMP_PLAY_START_SEPARATOR = "|"

/** Extras key holding the number of songs behind a playlist, album or artist item. */
/** Extras key holding when a playlist was last played (epoch millis, 0 if never). */
const val ACCESS_TIME_KEY = "com.example.android.uamp.ACCESS_TIME"

const val SONG_COUNT_KEY = "com.example.android.uamp.SONG_COUNT"
private const val UNKNOWN_ARTIST = "Unknown Artist"

const val MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"

const val RESOURCE_ROOT_URI = "android.resource://com.example.android.uamp.next/drawable/"
