package com.example.android.uamp.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.android.uamp.ui.theme.StrettoIcons
import com.example.android.uamp.viewmodels.songCount

private const val ORIGINAL_ARTWORK_URI_KEY = "com.example.android.uamp.JSON_ARTWORK_URI"

fun MediaItem.artworkModel(): String? {
    val original = mediaMetadata.extras?.getString(ORIGINAL_ARTWORK_URI_KEY)
    if (!original.isNullOrBlank()) return original
    val uri = mediaMetadata.artworkUri?.toString()
    return uri?.takeIf { it.startsWith("http") }
}

fun MediaItem.displayTitle(): String =
    (mediaMetadata.title ?: mediaMetadata.displayTitle)?.toString().orEmpty()

fun MediaItem.displayArtist(): String =
    (mediaMetadata.artist ?: mediaMetadata.albumTitle)?.toString().orEmpty()

fun MediaItem.songCountLabel(): String {
    val count = songCount()
    return if (count == 1) "1 song" else "$count songs"
}

/** Square album art with a soft blue placeholder while it loads or if it is missing. */
@Composable
fun ArtImage(
    item: MediaItem?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp)
) {
    val model = item?.artworkModel()
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            StrettoIcons.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxSize(0.38f)
        )
        if (model != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(300)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Bouncy press feedback for tappable cards. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.96f
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    val haptics = LocalHapticFeedback.current
    return this
        .scale(scale)
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick?.let {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    it()
                }
            }
        )
}

/** Fades and slides content in once, with a small delay so lists cascade in. */
@Composable
fun Modifier.staggerIn(index: Int, stepMs: Int = 45): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            tween(durationMillis = 420, delayMillis = (index.coerceAtMost(10)) * stepMs, easing = FastOutSlowInEasing)
        )
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 36.dp.toPx()
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/** Animated equalizer bars shown on the currently playing row. */
@Composable
fun PlayingBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val heights = (0 until 3).map { i ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(450 + i * 130, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        )
    }
    Row(
        modifier = modifier.size(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight(if (playing) h.value else 0.3f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun SongRow(
    song: MediaItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trackNumber: Int? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 0.985f)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtImage(song, Modifier.size(52.dp), RoundedCornerShape(12.dp))
            if (isCurrent) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    PlayingBars(isPlaying, Color.White)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.displayTitle(),
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.displayArtist(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A playlist/album tile: art, scrim, title and an optional quick-play button. */
@Composable
fun CollectionCard(
    item: MediaItem,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onPlay: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .pressable(onClick, onLongClick)
    ) {
        ArtImage(item, Modifier.fillMaxSize(), RoundedCornerShape(0.dp))
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f)
                    )
                )
        )
        if (pinned) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(StrettoIcons.Pin, null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = if (onPlay != null) 58.dp else 14.dp, bottom = 12.dp)
        ) {
            Text(
                item.displayTitle(),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val count = item.songCount()
            if (count > 0) {
                Text(
                    item.songCountLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }
        }
        if (onPlay != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .pressable(onPlay, pressedScale = 0.88f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    StrettoIcons.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp)
        )
    }
}
