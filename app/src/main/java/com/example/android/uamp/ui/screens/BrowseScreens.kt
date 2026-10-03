package com.example.android.uamp.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.example.android.uamp.ui.ArtImage
import com.example.android.uamp.ui.CollectionCard
import com.example.android.uamp.ui.EmptyState
import com.example.android.uamp.ui.RoundIconButton
import com.example.android.uamp.ui.SectionHeader
import com.example.android.uamp.ui.SongRow
import com.example.android.uamp.ui.displayArtist
import com.example.android.uamp.ui.displayTitle
import com.example.android.uamp.ui.pressable
import com.example.android.uamp.ui.songCountLabel
import com.example.android.uamp.ui.staggerIn
import com.example.android.uamp.ui.theme.StrettoIcons
import com.example.android.uamp.ui.theme.brandBrush
import com.example.android.uamp.viewmodels.StrettoViewModel

enum class CollectionKind { Playlist, Album, Artist }

@Composable
private fun ScreenTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp)) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PlaylistsScreen(
    vm: StrettoViewModel,
    listState: LazyListState,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by vm.playlists.collectAsState()
    val pinnedIds by vm.pinned.collectAsState()
    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("Playlists", "${playlists.size} playlists") }
        if (playlists.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().height(240.dp)) { EmptyState("Loading your playlists…") } }
        }
        itemsIndexed(playlists, key = { _, it -> it.mediaId }) { index, playlist ->
            val pinned = playlist.mediaId in pinnedIds
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable({ onOpen(playlist) }, pressedScale = 0.985f)
                    .padding(start = 20.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtImage(playlist, Modifier.size(58.dp), RoundedCornerShape(14.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.displayTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        playlist.songCountLabel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RoundIconButton(
                    if (pinned) StrettoIcons.Pin else StrettoIcons.PinOutline,
                    if (pinned) "Unpin" else "Pin to home",
                    { vm.togglePin(playlist.mediaId) },
                    tint = if (pinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                RoundIconButton(
                    StrettoIcons.PlayArrow,
                    "Play",
                    { vm.playCollection(playlist.mediaId, shuffle = false) },
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AlbumsScreen(
    vm: StrettoViewModel,
    gridState: LazyGridState,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val albums by vm.albums.collectAsState()
    Column(modifier) {
        ScreenTitle("Albums", "${albums.size} albums")
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums, key = { it.mediaId }) { album ->
                CollectionCard(
                    album,
                    modifier = Modifier.aspectRatio(1f),
                    onClick = { onOpen(album) },
                    onPlay = { vm.playCollection(album.mediaId, shuffle = false) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistsScreen(
    vm: StrettoViewModel,
    listState: LazyListState,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val artists by vm.artists.collectAsState()
    val grouped = remember(artists) {
        artists.groupBy { artist ->
            val first = artist.displayTitle().firstOrNull()?.uppercaseChar() ?: '#'
            if (first.isLetter()) first.toString() else "#"
        }.toSortedMap(compareBy<String> { it == "#" }.thenBy { it })
    }
    LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("Artists", "${artists.size} artists") }
        if (artists.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().height(240.dp)) { EmptyState("Loading your artists…") } }
        }
        grouped.forEach { (letter, items) ->
            stickyHeader(key = "h$letter") {
                Text(
                    letter,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }
            items(items, key = { it.mediaId }) { artist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable({ onOpen(artist) }, pressedScale = 0.985f)
                        .padding(horizontal = 20.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtImage(artist, Modifier.size(50.dp), CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            artist.displayTitle(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            artist.songCountLabel(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailScreen(
    vm: StrettoViewModel,
    item: MediaItem,
    kind: CollectionKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val songs by produceState<List<MediaItem>?>(initialValue = null, item.mediaId) {
        value = vm.childrenOf(item.mediaId)
    }
    val nowPlaying by vm.nowPlaying.observeAsState()
    val playbackState by vm.playbackState.observeAsState()
    val pinnedIds by vm.pinned.collectAsState()
    val pinned = item.mediaId in pinnedIds
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.55f else 0.9f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(bottom = 8.dp)
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    RoundIconButton(StrettoIcons.ArrowBack, "Back", onBack)
                    Spacer(Modifier.weight(1f))
                    if (kind == CollectionKind.Playlist) {
                        RoundIconButton(
                            if (pinned) StrettoIcons.Pin else StrettoIcons.PinOutline,
                            if (pinned) "Unpin" else "Pin to home",
                            { vm.togglePin(item.mediaId) },
                            tint = if (pinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ArtImage(
                        item,
                        Modifier
                            .size(210.dp)
                            .staggerIn(0),
                        if (kind == CollectionKind.Artist) CircleShape else RoundedCornerShape(26.dp)
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        item.displayTitle(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.staggerIn(1)
                    )
                    val count = songs?.size ?: 0
                    Text(
                        if (songs == null) " " else if (count == 1) "1 song" else "$count songs",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.staggerIn(2)
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.staggerIn(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PillButton(
                            "Play",
                            StrettoIcons.PlayArrow,
                            filled = true,
                            onClick = { vm.playCollection(item.mediaId, shuffle = false) },
                            modifier = Modifier.weight(1f)
                        )
                        PillButton(
                            "Shuffle",
                            StrettoIcons.Shuffle,
                            filled = false,
                            onClick = { vm.playCollection(item.mediaId, shuffle = true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        val list = songs
        if (list == null) {
            item { Box(Modifier.fillMaxWidth().height(160.dp)) { EmptyState("Loading…") } }
        } else {
            itemsIndexed(list, key = { i, s -> "$i-${s.mediaId}" }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = nowPlaying?.mediaId == song.mediaId,
                    isPlaying = playbackState?.isPlaying == true,
                    onClick = { vm.playSong(item.mediaId, song) },
                    modifier = Modifier.padding(top = if (index == 0) 8.dp else 0.dp)
                )
            }
        }
    }
}

@Composable
fun PillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val contentColor = if (filled) Color.White else MaterialTheme.colorScheme.primary
    Row(
        modifier
            .height(52.dp)
            .clip(CircleShape)
            .then(
                if (filled) Modifier.background(brandBrush(dark))
                else Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.6f else 1f))
            )
            .pressable(onClick, pressedScale = 0.95f),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (filled) contentColor else MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) contentColor else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun SearchScreen(
    vm: StrettoViewModel,
    onBack: () -> Unit,
    onOpenCollection: (MediaItem, CollectionKind) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    val songs by vm.songs.collectAsState()
    val artists by vm.artists.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val nowPlaying by vm.nowPlaying.observeAsState()
    val playbackState by vm.playbackState.observeAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val needle = query.trim().lowercase()
    val songResults by remember(songs, needle) {
        derivedStateOf {
            if (needle.length < 2) emptyList() else songs.filter {
                it.displayTitle().lowercase().contains(needle) ||
                    it.displayArtist().lowercase().contains(needle) ||
                    (it.mediaMetadata.albumTitle?.toString()?.lowercase()?.contains(needle) == true)
            }.take(150)
        }
    }
    val artistResults = remember(artists, needle) {
        if (needle.length < 2) emptyList() else artists.filter {
            it.displayTitle().lowercase().contains(needle)
        }.take(6)
    }
    val playlistResults = remember(playlists, needle) {
        if (needle.length < 2) emptyList() else playlists.filter {
            it.displayTitle().lowercase().contains(needle)
        }.take(6)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 6.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(StrettoIcons.ArrowBack, "Back", onBack)
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Songs, artists, playlists") },
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        RoundIconButton(StrettoIcons.Close, "Clear", { query = "" })
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus)
            )
        }
        if (needle.length < 2) {
            EmptyState("Search your whole library")
        } else if (songResults.isEmpty() && artistResults.isEmpty() && playlistResults.isEmpty()) {
            EmptyState("Nothing found for “$query”")
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                if (artistResults.isNotEmpty()) {
                    item { SectionHeader("Artists", Modifier.padding(vertical = 8.dp)) }
                    items(artistResults, key = { "a" + it.mediaId }) { artist ->
                        SearchCollectionRow(artist, CircleShape, "Artist") {
                            onOpenCollection(artist, CollectionKind.Artist)
                        }
                    }
                }
                if (playlistResults.isNotEmpty()) {
                    item { SectionHeader("Playlists", Modifier.padding(vertical = 8.dp)) }
                    items(playlistResults, key = { "p" + it.mediaId }) { playlist ->
                        SearchCollectionRow(playlist, RoundedCornerShape(12.dp), playlist.songCountLabel()) {
                            onOpenCollection(playlist, CollectionKind.Playlist)
                        }
                    }
                }
                if (songResults.isNotEmpty()) {
                    item { SectionHeader("Songs", Modifier.padding(vertical = 8.dp)) }
                    itemsIndexed(songResults, key = { _, s -> "s" + s.mediaId }) { index, song ->
                        SongRow(
                            song = song,
                            isCurrent = nowPlaying?.mediaId == song.mediaId,
                            isPlaying = playbackState?.isPlaying == true,
                            onClick = { vm.playQueue(songResults, index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCollectionRow(
    item: MediaItem,
    shape: androidx.compose.ui.graphics.Shape,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 0.985f)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtImage(item, Modifier.size(50.dp), shape)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(item.displayTitle(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
