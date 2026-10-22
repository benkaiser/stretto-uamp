package com.example.android.uamp.ui.screens

import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.mediarouter.app.MediaRouteButton
import com.example.android.uamp.R
import com.example.android.uamp.common.NOTHING_PLAYING
import com.example.android.uamp.media.library.ACCESS_TIME_KEY
import com.example.android.uamp.ui.ArtImage
import com.example.android.uamp.ui.CollectionCard
import com.example.android.uamp.ui.RoundIconButton
import com.example.android.uamp.ui.SectionHeader
import com.example.android.uamp.ui.displayArtist
import com.example.android.uamp.ui.displayTitle
import com.example.android.uamp.ui.pressable
import com.example.android.uamp.ui.staggerIn
import com.example.android.uamp.ui.theme.StrettoIcons
import com.example.android.uamp.ui.theme.brandBrush
import com.example.android.uamp.viewmodels.LIBRARY_PLAYLIST_ID
import com.example.android.uamp.viewmodels.StrettoViewModel
import com.google.android.gms.cast.framework.CastButtonFactory
import java.text.NumberFormat
import java.util.Calendar

@Composable
fun HomeScreen(
    vm: StrettoViewModel,
    listState: LazyListState,
    onOpenPlaylist: (MediaItem) -> Unit,
    onSearch: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlists by vm.playlists.collectAsState()
    val pinnedIds by vm.pinned.collectAsState()
    val songs by vm.songs.collectAsState()
    val nowPlaying by vm.nowPlaying.observeAsState()
    val playbackState by vm.playbackState.observeAsState()
    val context = LocalContext.current

    val byId = playlists.associateBy { it.mediaId }
    val pinned = pinnedIds.mapNotNull { byId[it] }
    val recent = playlists.filter {
        it.mediaId !in pinnedIds &&
            (it.mediaMetadata.extras?.getLong(ACCESS_TIME_KEY, 0L) ?: 0L) > 0L
    }.take(12)
    val hasNowPlaying = nowPlaying != null && nowPlaying != NOTHING_PLAYING
    val isPlaying = playbackState?.isPlaying == true

    fun togglePin(item: MediaItem) {
        val wasPinned = vm.isPinned(item.mediaId)
        vm.togglePin(item.mediaId)
        Toast.makeText(
            context,
            if (wasPinned) "Unpinned ${item.displayTitle()}" else "Pinned ${item.displayTitle()}",
            Toast.LENGTH_SHORT
        ).show()
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item(key = "header") {
            HomeHeader(vm, onSearch, Modifier.staggerIn(0))
        }
        item(key = "hero") {
            ShuffleHero(
                songCount = songs.size,
                onClick = vm::shuffleLibrary,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .staggerIn(1)
            )
        }
        item(key = "quick") {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .staggerIn(2),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuickTile(
                    title = "Newest first",
                    subtitle = "Library, latest adds on top",
                    icon = StrettoIcons.History,
                    onClick = vm::playLibraryNewestFirst,
                    modifier = Modifier.weight(1f)
                )
                AnimatedContent(
                    targetState = if (hasNowPlaying) nowPlaying else null,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    modifier = Modifier.weight(1f),
                    label = "continue"
                ) { item ->
                    if (item != null) {
                        ContinueTile(
                            item = item,
                            isPlaying = isPlaying,
                            onToggle = vm::togglePlayPause,
                            onOpen = onOpenNowPlaying
                        )
                    } else {
                        QuickTile(
                            title = "Browse",
                            subtitle = "Playlists, albums, artists",
                            icon = StrettoIcons.Playlists,
                            onClick = {},
                            enabled = false
                        )
                    }
                }
            }
        }
        item(key = "pinned") {
            Column(Modifier.staggerIn(3), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader("Pinned") {
                    Icon(
                        StrettoIcons.Pin,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (pinned.isEmpty()) {
                    PinHint(Modifier.padding(horizontal = 20.dp))
                } else {
                    pinned.chunked(2).forEach { pair ->
                        Row(
                            Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            pair.forEach { playlist ->
                                CollectionCard(
                                    playlist,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.3f),
                                    pinned = true,
                                    onClick = { onOpenPlaylist(playlist) },
                                    onLongClick = { togglePin(playlist) },
                                    onPlay = { vm.playCollection(playlist.mediaId, shuffle = false) }
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item(key = "recent") {
                Column(Modifier.staggerIn(4), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Recently played")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(recent, key = { it.mediaId }) { playlist ->
                            CollectionCard(
                                playlist,
                                modifier = Modifier.width(158.dp).aspectRatio(1f),
                                onClick = { onOpenPlaylist(playlist) },
                                onLongClick = { togglePin(playlist) },
                                onPlay = { vm.playCollection(playlist.mediaId, shuffle = false) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(vm: StrettoViewModel, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                greeting,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Stretto",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        CastButton()
        RoundIconButton(StrettoIcons.Search, "Search", onSearch)
        AccountMenu(vm, Modifier.padding(start = 4.dp, end = 12.dp))
    }
}

@Composable
private fun CastButton() {
    val context = LocalContext.current
    val available = androidx.compose.runtime.remember {
        runCatching { com.google.android.gms.cast.framework.CastContext.getSharedInstance(context) }
            .isSuccess
    }
    if (!available) return
    AndroidView(
        factory = { ctx ->
            MediaRouteButton(ContextThemeWrapper(ctx, R.style.CastButtonTheme)).also {
                runCatching { CastButtonFactory.setUpMediaRouteButton(ctx, it) }
            }
        },
        modifier = Modifier.size(48.dp)
    )
}

@Composable
private fun ShuffleHero(songCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "hero")
    val sway by transition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "sway"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(brandBrush(androidx.compose.foundation.isSystemInDarkTheme()))
            .pressable(onClick, pressedScale = 0.97f)
    ) {
        Icon(
            StrettoIcons.Shuffle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .size(190.dp)
                .rotate(sway)
                .alpha(0.16f)
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
        ) {
            Text(
                "Shuffle library",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (songCount > 0) "${NumberFormat.getIntegerInstance().format(songCount)} songs, all random"
                else "Your whole collection, random",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(22.dp)
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                StrettoIcons.PlayArrow,
                contentDescription = "Shuffle library",
                tint = Color(0xFF0064AE),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(
        modifier
            .height(132.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (enabled) Modifier.pressable(onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContinueTile(
    item: MediaItem,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    Box(
        Modifier
            .height(132.dp)
            .clip(RoundedCornerShape(24.dp))
            .pressable(onOpen)
    ) {
        ArtImage(item, Modifier.fillMaxWidth().height(132.dp), RoundedCornerShape(0.dp))
        Box(
            Modifier
                .matchParentSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.2f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Black.copy(alpha = 0.8f)
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
                .pressable(onToggle, pressedScale = 0.88f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isPlaying) StrettoIcons.Pause else StrettoIcons.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color(0xFF0064AE),
                modifier = Modifier.size(24.dp)
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                "Continue",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
            Text(
                item.displayTitle(),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.displayArtist(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PinHint(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            StrettoIcons.PinOutline,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            "Pin your favourite playlists to keep them here. Tap the pin in Playlists, or long-press any playlist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
