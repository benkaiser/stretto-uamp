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

package com.example.android.uamp

import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.media3.common.MediaItem
import com.example.android.uamp.common.NOTHING_PLAYING
import com.example.android.uamp.databinding.ActivityMainBinding
import com.example.android.uamp.fragments.MediaItemFragment
import com.example.android.uamp.fragments.NowPlayingFragment
import com.example.android.uamp.fragments.SearchFragment
import com.example.android.uamp.media.MusicService
import com.example.android.uamp.utils.Event
import com.example.android.uamp.utils.InjectorUtils
import com.example.android.uamp.viewmodels.MainActivityViewModel
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext

class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<MainActivityViewModel> {
        InjectorUtils.provideMainActivityViewModel(this)
    }
    private var castContext: CastContext? = null
    private lateinit var binding: ActivityMainBinding

    private val handler = Handler(Looper.getMainLooper())
    private var updateMiniPlayerPosition = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the Cast context. This is required so that the media route button can be
        // created in the AppBar
        castContext = CastContext.getSharedInstance(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Since UAMP is a music player, the volume controls should adjust the music volume while
        // in the app.
        volumeControlStream = AudioManager.STREAM_MUSIC

        /**
         * Observe [MainActivityViewModel.navigateToFragment] for [Event]s that request a
         * fragment swap.
         */
        viewModel.navigateToFragment.observe(this, Observer {
            it?.getContentIfNotHandled()?.let { fragmentRequest ->
                val transaction = supportFragmentManager.beginTransaction()
                transaction.replace(
                    R.id.fragmentContainer, fragmentRequest.fragment, fragmentRequest.tag
                )
                if (fragmentRequest.backStack) transaction.addToBackStack(null)
                transaction.commit()
                // Update mini-player visibility after fragment transaction commits
                binding.root.post { updateMiniPlayerVisibility() }
            }
        })

        /**
         * Observe changes to the [MainActivityViewModel.rootMediaItem]. When the app starts,
         * and the UI connects to [MusicService], this will be updated and the app will show
         * the initial list of media items.
         */
        viewModel.rootMediaItem.observe(this,
            Observer<MediaItem> { rootMediaItem ->
                rootMediaItem?.let { navigateToMediaItem(it.mediaId) }
            })

        /**
         * Observe [MainActivityViewModel.navigateToMediaItem] for [Event]s indicating
         * the user has requested to browse to a different [MediaItemData].
         */
        viewModel.navigateToMediaItem.observe(this, Observer {
            it?.getContentIfNotHandled()?.let { mediaId ->
                navigateToMediaItem(mediaId)
            }
        })

        // Observe now playing to update mini-player title/artist
        viewModel.nowPlaying.observe(this, Observer { mediaItem ->
            if (mediaItem != null && mediaItem != NOTHING_PLAYING) {
                binding.miniPlayer.miniPlayerTitle.text = mediaItem.mediaMetadata.title
                binding.miniPlayer.miniPlayerArtist.text = mediaItem.mediaMetadata.artist
                    ?: mediaItem.mediaMetadata.albumTitle
            }
            updateMiniPlayerVisibility()
        })

        // Observe playback state to update mini-player play/pause icon
        viewModel.playbackState.observe(this, Observer { playbackState ->
            val res = if (playbackState.isPlaying) {
                R.drawable.ic_pause_black_24dp
            } else {
                R.drawable.ic_play_arrow_black_24dp
            }
            binding.miniPlayer.miniPlayerPlayPause.setImageResource(res)
        })

        // Mini-player bar click → open NowPlayingFragment
        binding.miniPlayer.miniPlayerContainer.setOnClickListener {
            viewModel.showFragment(NowPlayingFragment.newInstance())
        }

        // Mini-player play/pause button
        binding.miniPlayer.miniPlayerPlayPause.setOnClickListener {
            val player = viewModel.player ?: return@setOnClickListener
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }

        // Mini-player next button
        binding.miniPlayer.miniPlayerNext.setOnClickListener {
            viewModel.player?.seekToNext()
        }

        // Listen for back stack changes to update mini-player visibility
        supportFragmentManager.addOnBackStackChangedListener {
            binding.root.post { updateMiniPlayerVisibility() }
        }
    }

    override fun onStart() {
        super.onStart()
        updateMiniPlayerPosition = true
        checkMiniPlayerPosition()
    }

    override fun onStop() {
        super.onStop()
        updateMiniPlayerPosition = false
    }

    /**
     * Polls the player position at ~1s intervals and updates the mini-player progress bar.
     */
    private fun checkMiniPlayerPosition() {
        handler.postDelayed({
            val player = viewModel.player
            if (player != null) {
                val duration = player.duration
                val position = player.currentPosition
                if (duration > 0) {
                    binding.miniPlayer.miniPlayerProgress.progress = (position * 1000 / duration).toInt()
                }
            }
            if (updateMiniPlayerPosition) {
                checkMiniPlayerPosition()
            }
        }, 1000)
    }

    /**
     * Shows the mini-player when something is playing AND the current fragment
     * is not NowPlayingFragment.
     */
    private fun updateMiniPlayerVisibility() {
        val hasNowPlaying = viewModel.nowPlaying.value != null
                && viewModel.nowPlaying.value != NOTHING_PLAYING
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        val isNowPlayingVisible = currentFragment is NowPlayingFragment

        binding.miniPlayer.miniPlayerContainer.visibility =
            if (hasNowPlaying && !isNowPlayingVisible) View.VISIBLE else View.GONE
    }

    @Override
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(com.example.android.uamp.media.R.menu.main_activity_menu, menu)

        /**
         * Set up a MediaRouteButton to allow the user to control the current media playback route
         */
        menu?.let {
            CastButtonFactory.setUpMediaRouteButton(this, menu, com.example.android.uamp.media.R.id.media_route_menu_item)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.example.android.uamp.media.R.id.search_menu_item -> {
                val searchFragment = SearchFragment.newInstance()
                viewModel.showFragment(searchFragment, backStack = true, tag = "search")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun navigateToMediaItem(mediaId: String) {
        var fragment: MediaItemFragment? = getBrowseFragment(mediaId)
        if (fragment == null) {
            fragment = MediaItemFragment.newInstance(mediaId)
            // If this is not the top level media (root), we add it to the fragment
            // back stack, so that actionbar toggle and Back will work appropriately:
            viewModel.showFragment(fragment, !isRootId(mediaId), mediaId)
        }
    }

    private fun isRootId(mediaId: String) = mediaId == viewModel.rootMediaItem.value?.mediaId

    private fun getBrowseFragment(mediaId: String): MediaItemFragment? {
        return supportFragmentManager.findFragmentByTag(mediaId) as? MediaItemFragment
    }
}
