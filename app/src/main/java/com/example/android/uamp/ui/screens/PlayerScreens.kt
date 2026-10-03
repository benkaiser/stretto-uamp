package com.example.android.uamp.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.example.android.uamp.common.NOTHING_PLAYING
import com.example.android.uamp.ui.ArtImage
import com.example.android.uamp.ui.displayArtist
import com.example.android.uamp.ui.displayTitle
import com.example.android.uamp.ui.pressable
import com.example.android.uamp.ui.theme.StrettoIcons
import com.example.android.uamp.ui.theme.brandBrush
import com.example.android.uamp.viewmodels.StrettoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Current (position, duration) in ms, polled while the screen is visible. */
@Composable
private fun rememberProgress(vm: StrettoViewModel, isPlaying: Boolean, mediaId: String?): State<Pair<Long, Long>> =
    produceState(initialValue = 0L to 0L, isPlaying, mediaId) {
        while (true) {
            vm.player?.let { player ->
                value = player.currentPosition.coerceAtLeast(0L) to player.duration.coerceAtLeast(0L)
            }
            delay(if (isPlaying) 250 else 1000)
        }
    }

@Composable
fun MiniPlayer(vm: StrettoViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val item by vm.nowPlaying.observeAsState()
    val state by vm.playbackState.observeAsState()
    val current = item?.takeIf { it != NOTHING_PLAYING } ?: return
    val isPlaying = state?.isPlaying == true
    val progress by rememberProgress(vm, isPlaying, current.mediaId)
    val fraction = if (progress.second > 0) (progress.first.toFloat() / progress.second).coerceIn(0f, 1f) else 0f
    val animatedFraction by animateFloatAsState(fraction, tween(300), label = "miniProgress")

    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(14.dp, RoundedCornerShape(24.dp), ambientColor = Color(0xFF0A72CC), spotColor = Color(0xFF0A72CC))
            .clip(RoundedCornerShape(24.dp))
            .pressable(onOpen, pressedScale = 0.985f),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column {
            Row(
                Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtImage(current, Modifier.size(46.dp), RoundedCornerShape(14.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = current.displayTitle(),
                        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                        label = "miniTitle"
                    ) { title ->
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        current.displayArtist(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .pressable(vm::togglePlayPause, pressedScale = 0.86f),
                    contentAlignment = Alignment.Center
                ) {
                    PlayPauseIcon(isPlaying, MaterialTheme.colorScheme.onPrimary, Modifier.size(26.dp))
                }
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .pressable(vm::next, pressedScale = 0.86f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        StrettoIcons.SkipNext,
                        "Next",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .background(brandBrush(isSystemInDarkTheme()))
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlayPauseIcon(isPlaying: Boolean, tint: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = isPlaying,
        transitionSpec = {
            (fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.6f)) togetherWith
                (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.6f))
        },
        label = "playPause"
    ) { playing ->
        Icon(
            if (playing) StrettoIcons.Pause else StrettoIcons.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            tint = tint,
            modifier = modifier
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@Composable
fun NowPlayingScreen(vm: StrettoViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val item by vm.nowPlaying.observeAsState()
    val state by vm.playbackState.observeAsState()
    val current: MediaItem = item?.takeIf { it != NOTHING_PLAYING } ?: MediaItem.EMPTY
    val isPlaying = state?.isPlaying == true
    val dark = isSystemInDarkTheme()
    val progress by rememberProgress(vm, isPlaying, current.mediaId)
    val (position, duration) = progress

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shownFraction = if (dragging) dragValue
    else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    // The artwork breathes in when playing and settles back when paused.
    val artScale by animateFloatAsState(
        if (isPlaying) 1f else 0.88f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "artScale"
    )

    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value > heightPx * 0.22f) onClose()
                            else offsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = { scope.launch { offsetY.animateTo(0f) } },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        scope.launch { offsetY.snapTo((offsetY.value + amount).coerceAtLeast(0f)) }
                    }
                )
            }
            .androidxOffset(offsetY.value)
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (dark) 0.34f else 0.30f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .pressable(onClose, pressedScale = 0.88f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(StrettoIcons.ExpandMore, "Close", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(30.dp))
                }
                Text(
                    "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.width(44.dp))
            }

            Spacer(Modifier.weight(0.6f))
            ArtImage(
                current,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .scale(artScale)
                    .shadow(28.dp, RoundedCornerShape(32.dp), ambientColor = Color(0xFF0A72CC), spotColor = Color(0xFF0A72CC)),
                RoundedCornerShape(32.dp)
            )
            Spacer(Modifier.weight(0.5f))

            AnimatedContent(
                targetState = current.mediaId to current.displayTitle() to current.displayArtist(),
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                label = "titles"
            ) { (idAndTitle, artist) ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        idAndTitle.second,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Slider(
                value = shownFraction,
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    if (duration > 0) vm.seekTo((dragValue * duration).toLong())
                    dragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    formatTime(if (dragging) (dragValue * duration).toLong() else position),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatTime(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .pressable(vm::previous, pressedScale = 0.86f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(StrettoIcons.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                }
                Box(
                    Modifier
                        .size(84.dp)
                        .shadow(18.dp, CircleShape, ambientColor = Color(0xFF0A72CC), spotColor = Color(0xFF0A72CC))
                        .clip(CircleShape)
                        .background(brandBrush(dark))
                        .pressable(vm::togglePlayPause, pressedScale = 0.9f),
                    contentAlignment = Alignment.Center
                ) {
                    PlayPauseIcon(isPlaying, Color.White, Modifier.size(44.dp))
                }
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .pressable(vm::next, pressedScale = 0.86f),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(StrettoIcons.SkipNext, "Next", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp))
                }
            }
            Spacer(Modifier.weight(0.5f))
        }
    }
}

private fun Modifier.androidxOffset(y: Float): Modifier =
    this.then(Modifier.offset { IntOffset(0, y.roundToInt()) })
