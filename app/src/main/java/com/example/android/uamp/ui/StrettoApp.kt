package com.example.android.uamp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.example.android.uamp.common.NOTHING_PLAYING
import com.example.android.uamp.ui.screens.AlbumsScreen
import com.example.android.uamp.ui.screens.ArtistsScreen
import com.example.android.uamp.ui.screens.CollectionKind
import com.example.android.uamp.ui.screens.LibraryStatusScreen
import com.example.android.uamp.ui.screens.SignInScreen
import com.example.android.uamp.ui.screens.DetailScreen
import com.example.android.uamp.ui.screens.HomeScreen
import com.example.android.uamp.ui.screens.MiniPlayer
import com.example.android.uamp.ui.screens.NowPlayingScreen
import com.example.android.uamp.ui.screens.PlaylistsScreen
import com.example.android.uamp.ui.screens.SearchScreen
import com.example.android.uamp.ui.theme.StrettoIcons
import com.example.android.uamp.viewmodels.StrettoViewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", StrettoIcons.Home),
    Playlists("Playlists", StrettoIcons.Playlists),
    Albums("Albums", StrettoIcons.Album),
    Artists("Artists", StrettoIcons.Person)
}

private data class DetailRoute(val item: MediaItem, val kind: CollectionKind)

/** What is currently on screen; [depth] drives the push/pop direction of the transition. */
private data class Destination(
    val tab: Tab,
    val detail: DetailRoute? = null,
    val search: Boolean = false,
    val depth: Int = 0
)

private enum class Gate { SignIn, Library, App }

private val ease = FastOutSlowInEasing

@Composable
fun StrettoApp(vm: StrettoViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val stack = remember { mutableStateListOf<DetailRoute>() }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }

    val homeState = rememberLazyListState()
    val playlistsState = rememberLazyListState()
    val artistsState = rememberLazyListState()
    val albumsState = rememberLazyGridState()

    val nowPlaying by vm.nowPlaying.observeAsState()
    val hasNowPlaying = nowPlaying != null && nowPlaying != NOTHING_PLAYING

    val destination = when {
        searchOpen -> Destination(tab, search = true, depth = stack.size + 1)
        stack.isNotEmpty() -> Destination(tab, stack.last(), depth = stack.size)
        else -> Destination(tab)
    }

    fun openDetail(item: MediaItem, kind: CollectionKind) {
        searchOpen = false
        stack.add(DetailRoute(item, kind))
    }

    BackHandler(enabled = searchOpen || stack.isNotEmpty()) {
        when {
            searchOpen -> searchOpen = false
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
        }
    }
    // Registered last so it takes priority over the navigation back handler above.
    BackHandler(enabled = nowPlayingOpen) { nowPlayingOpen = false }

    val account by vm.accountState.collectAsState()
    val songs by vm.songs.collectAsState()
    val gate = when {
        !account.signedIn -> Gate.SignIn
        songs.isEmpty() -> Gate.Library
        else -> Gate.App
    }

    LaunchedEffect(gate) {
        if (gate != Gate.App) {
            tab = Tab.Home
            stack.clear()
            searchOpen = false
            nowPlayingOpen = false
        }
    }

    AnimatedContent(
        targetState = gate,
        transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        label = "gate"
    ) { current ->
        when (current) {
            Gate.SignIn -> SignInScreen(vm)
            Gate.Library -> LibraryStatusScreen(vm)
            Gate.App -> Unit
        }
    }
    if (gate != Gate.App) return

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = hasNowPlaying && !nowPlayingOpen,
                        enter = slideInVertically(tween(400, easing = ease)) { it } + fadeIn(tween(400)),
                        exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(200))
                    ) {
                        MiniPlayer(vm, onOpen = { nowPlayingOpen = true })
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        Tab.values().forEach { entry ->
                            NavigationBarItem(
                                selected = tab == entry,
                                onClick = {
                                    searchOpen = false
                                    stack.clear()
                                    tab = entry
                                },
                                icon = { Icon(entry.icon, contentDescription = entry.label) },
                                label = { Text(entry.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            AnimatedContent(
                targetState = destination,
                modifier = Modifier.padding(padding),
                transitionSpec = {
                    val forward = targetState.depth > initialState.depth
                    val backward = targetState.depth < initialState.depth
                    when {
                        forward -> (slideInHorizontally(tween(340, easing = ease)) { it / 3 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(340, easing = ease)) { -it / 6 } + fadeOut(tween(200)))
                        backward -> (slideInHorizontally(tween(340, easing = ease)) { -it / 6 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(340, easing = ease)) { it / 3 } + fadeOut(tween(200)))
                        else -> fadeIn(tween(260, delayMillis = 60)) togetherWith fadeOut(tween(140))
                    }
                },
                label = "navigation"
            ) { dest ->
                val fill = Modifier.fillMaxSize()
                when {
                    dest.search -> SearchScreen(
                        vm,
                        onBack = { searchOpen = false },
                        onOpenCollection = ::openDetail,
                        modifier = fill
                    )
                    dest.detail != null -> DetailScreen(
                        vm,
                        item = dest.detail.item,
                        kind = dest.detail.kind,
                        onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
                        modifier = fill
                    )
                    else -> when (dest.tab) {
                        Tab.Home -> HomeScreen(
                            vm,
                            homeState,
                            onOpenPlaylist = { openDetail(it, CollectionKind.Playlist) },
                            onSearch = { searchOpen = true },
                            onOpenNowPlaying = { nowPlayingOpen = true },
                            modifier = fill
                        )
                        Tab.Playlists -> PlaylistsScreen(
                            vm, playlistsState,
                            onOpen = { openDetail(it, CollectionKind.Playlist) },
                            modifier = fill
                        )
                        Tab.Albums -> AlbumsScreen(
                            vm, albumsState,
                            onOpen = { openDetail(it, CollectionKind.Album) },
                            modifier = fill
                        )
                        Tab.Artists -> ArtistsScreen(
                            vm, artistsState,
                            onOpen = { openDetail(it, CollectionKind.Artist) },
                            modifier = fill
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = nowPlayingOpen && hasNowPlaying,
            enter = slideInVertically(tween(420, easing = ease)) { it } + fadeIn(tween(300)),
            exit = slideOutVertically(tween(320, easing = ease)) { it } + fadeOut(tween(250))
        ) {
            NowPlayingScreen(vm, onClose = { nowPlayingOpen = false })
        }
    }
}
