package com.example.android.uamp.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.android.uamp.common.MusicServiceConnection
import com.example.android.uamp.common.NOTHING_PLAYING
import com.example.android.uamp.common.PlaybackState
import com.example.android.uamp.media.library.SONG_COUNT_KEY
import com.example.android.uamp.media.library.UAMP_ALBUMS_ROOT
import com.example.android.uamp.media.library.UAMP_ARTISTS_ROOT
import com.example.android.uamp.media.library.UAMP_PLAYLISTS_ROOT
import com.example.android.uamp.media.library.UAMP_PLAY_PREFIX
import com.example.android.uamp.media.library.UAMP_PLAY_START_SEPARATOR
import com.example.android.uamp.media.library.UAMP_RECOMMENDED_ROOT
import com.example.android.uamp.media.library.UAMP_SHUFFLE_PREFIX
import com.example.android.uamp.media.library.UAMP_SONGS_ROOT
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Name of the catalog playlist that holds every song, newest first. */
const val LIBRARY_PLAYLIST_ID = "Library"

/** Single view model behind the whole Compose UI. */
class StrettoViewModel(
    application: Application,
    private val connection: MusicServiceConnection
) : AndroidViewModel(application) {

    private val pinStore = PinStore(application)

    val nowPlaying: LiveData<MediaItem> = connection.nowPlaying
    val playbackState: LiveData<PlaybackState> = connection.playbackState
    val player: Player? get() = connection.player

    /** Playlists in most-recently-used order. */
    private val _playlists = MutableStateFlow<List<MediaItem>>(emptyList())
    val playlists: StateFlow<List<MediaItem>> = _playlists.asStateFlow()

    private val _albums = MutableStateFlow<List<MediaItem>>(emptyList())
    val albums: StateFlow<List<MediaItem>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<MediaItem>>(emptyList())
    val artists: StateFlow<List<MediaItem>> = _artists.asStateFlow()

    private val _songs = MutableStateFlow<List<MediaItem>>(emptyList())
    val songs: StateFlow<List<MediaItem>> = _songs.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _pinned = MutableStateFlow(pinStore.load())
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    private val rootObserver = Observer<MediaItem> { root ->
        if (root != null && root != MediaItem.EMPTY) refresh()
    }

    private var lastNowPlayingId: String? = null
    private val nowPlayingObserver = Observer<MediaItem> { item ->
        val id = item?.mediaId
        if (id != lastNowPlayingId && item != NOTHING_PLAYING) {
            lastNowPlayingId = id
            // Playing a playlist moves it to the front of the most-recently-used list.
            if (_loaded.value) refreshPlaylists()
        }
    }

    init {
        connection.rootMediaItem.observeForever(rootObserver)
        connection.nowPlaying.observeForever(nowPlayingObserver)
    }

    override fun onCleared() {
        connection.rootMediaItem.removeObserver(rootObserver)
        connection.nowPlaying.removeObserver(nowPlayingObserver)
    }

    fun refresh() {
        viewModelScope.launch {
            // The catalog may still be downloading on a first run, so retry while it is empty.
            repeat(8) {
                _playlists.value = connection.getChildren(UAMP_PLAYLISTS_ROOT)
                _albums.value = connection.getChildren(UAMP_ALBUMS_ROOT)
                _artists.value = connection.getChildren(UAMP_ARTISTS_ROOT)
                _songs.value = connection.getChildren(UAMP_SONGS_ROOT)
                _loaded.value = true
                if (_songs.value.isNotEmpty()) return@launch
                delay(2000)
            }
        }
    }

    private fun refreshPlaylists() {
        viewModelScope.launch {
            val fresh = connection.getChildren(UAMP_PLAYLISTS_ROOT)
            if (fresh.isNotEmpty()) _playlists.value = fresh
        }
    }

    suspend fun childrenOf(parentId: String): List<MediaItem> =
        connection.getChildren(parentId).filter {
            it.mediaMetadata.isPlayable == true && !it.mediaId.startsWith(UAMP_SHUFFLE_PREFIX)
        }

    // ---- Pinning ----

    fun isPinned(playlistId: String) = playlistId in _pinned.value

    fun togglePin(playlistId: String) {
        val updated = if (isPinned(playlistId)) {
            _pinned.value - playlistId
        } else {
            _pinned.value + playlistId
        }
        _pinned.value = updated
        pinStore.save(updated)
    }

    // ---- Playback ----

    private fun startWith(item: MediaItem) {
        val player = player ?: return
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    private fun virtualItem(id: String) = MediaItem.Builder().setMediaId(id).build()

    /** Shuffles every song in the library. */
    fun shuffleLibrary() = startWith(virtualItem("$UAMP_SHUFFLE_PREFIX$UAMP_RECOMMENDED_ROOT"))

    /** Plays the whole library in order, most recently added first. */
    fun playLibraryNewestFirst() {
        val libraryId = _playlists.value.firstOrNull { it.mediaId == LIBRARY_PLAYLIST_ID }?.mediaId
            ?: UAMP_SONGS_ROOT
        playCollection(libraryId, shuffle = false)
    }

    /** Plays a playlist, album or artist, either in order or shuffled. */
    fun playCollection(parentId: String, shuffle: Boolean) {
        val prefix = if (shuffle) UAMP_SHUFFLE_PREFIX else UAMP_PLAY_PREFIX
        startWith(virtualItem("$prefix$parentId"))
    }

    /** Plays [song] and then continues through [parentId] in order. */
    fun playSong(parentId: String, song: MediaItem) {
        val current = nowPlaying.value
        val player = player ?: return
        if (current?.mediaId == song.mediaId && player.playbackState != Player.STATE_IDLE) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        startWith(virtualItem("$UAMP_PLAY_PREFIX$parentId$UAMP_PLAY_START_SEPARATOR${song.mediaId}"))
    }

    /** Plays a hand-picked list (e.g. search results) starting at [index]. */
    fun playQueue(items: List<MediaItem>, index: Int) {
        val player = player ?: return
        player.setMediaItems(items, index, C.TIME_UNSET)
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = player ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun next() { player?.seekToNext() }

    fun previous() {
        val player = player ?: return
        // Restart the song if it has been playing a while, otherwise go to the previous one.
        if (player.currentPosition > 4000 || !player.hasPreviousMediaItem()) {
            player.seekTo(0)
        } else {
            player.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) { player?.seekTo(positionMs) }

    class Factory(
        private val application: Application,
        private val connection: MusicServiceConnection
    ) : ViewModelProvider.NewInstanceFactory() {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StrettoViewModel(application, connection) as T
    }
}

fun MediaItem.songCount(): Int = mediaMetadata.extras?.getInt(SONG_COUNT_KEY, 0) ?: 0
