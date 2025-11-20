/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.contains
import androidx.navigation.ui.setupWithNavController
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.activities.base.AbsCastActivity
import code.name.monkey.retromusic.extensions.*
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.SearchQueryHelper.getSongs
import code.name.monkey.retromusic.interfaces.IScrollHelper
import code.name.monkey.retromusic.interfaces.IMiniPlayerExpanded
import code.name.monkey.retromusic.model.CategoryInfo
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.repository.PlaylistSongsLoader
import code.name.monkey.retromusic.service.MusicService
import code.name.monkey.retromusic.util.AppRater
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.logE
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get

class MainActivity : AbsCastActivity(), IMiniPlayerExpanded {

    companion object {
        const val TAG = "MainActivity"
        const val EXPAND_PANEL = "expand_panel"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTaskDescriptionColorAuto()
        hideStatusBar()
        updateTabs()
        AppRater.appLaunched(this)

        setupNavigationController()

        // Restore navigation state if present
        savedInstanceState?.getBundle("nav_state")?.let {
            findNavController(R.id.fragment_container).restoreState(it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("nav_state", findNavController(R.id.fragment_container).saveState())
    }

    private fun setupNavigationController() {
        val navController = findNavController(R.id.fragment_container)
        val navGraph = navController.navInflater.inflate(R.navigation.main_graph)

        val categoryInfo = PreferenceUtil.libraryCategory.firstOrNull { it.visible } ?: return

        if (!navGraph.contains(PreferenceUtil.lastTab)) {
            PreferenceUtil.lastTab = categoryInfo.category.id
        }

        navGraph.setStartDestination(
            if (PreferenceUtil.rememberLastTab) {
                PreferenceUtil.lastTab.takeIf { it != 0 } ?: categoryInfo.category.id
            } else categoryInfo.category.id
        )

        navController.graph = navGraph
        val startDestinationId = navGraph.startDestinationId

        navigationView.setupWithNavController(navController)
        navigationView.setOnItemReselectedListener {
            currentFragment(R.id.fragment_container).apply {
                if (this is IScrollHelper) scrollToTop()
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == startDestinationId) {
                currentFragment(R.id.fragment_container)?.enterTransition = null
            }

            when (destination.id) {
                R.id.action_home,
                R.id.action_song,
                R.id.action_album,
                R.id.action_artist,
                R.id.action_folder,
                R.id.action_playlist,
                R.id.action_genre,
                R.id.action_search -> {
                    if (PreferenceUtil.rememberLastTab) saveTab(destination.id)
                    setBottomNavVisibility(visible = true, animate = true)
                }

                R.id.playing_queue_fragment,
                R.id.action_sample -> {
                    setBottomNavVisibility(visible = false, hideBottomSheet = true)
                }

                else -> setBottomNavVisibility(visible = false, animate = true)
            }
        }
    }

    private fun saveTab(id: Int) {
        if (PreferenceUtil.libraryCategory.firstOrNull { it.category.id == id }?.visible == true) {
            PreferenceUtil.lastTab = id
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        findNavController(R.id.fragment_container).navigateUp()

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val expand = intent?.extra<Boolean>(EXPAND_PANEL)?.value ?: false
        if (expand && PreferenceUtil.isExpandPanel) {
            fromNotification = true
            slidingPanel.bringToFront()
            expandPanel()
            intent?.removeExtra(EXPAND_PANEL)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        intent?.let { handlePlaybackIntent(it) }
    }

    private fun handlePlaybackIntent(intent: Intent) {
        lifecycleScope.launch(IO) {
            val handled = when {
                intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH ->
                    handleSearchIntent(intent)

                intent.data?.toString()?.isNotEmpty() == true ->
                    handleUriIntent(intent.data!!)

                MediaStore.Audio.Playlists.CONTENT_TYPE == intent.type ->
                    handlePlaylistIntent(intent)

                MediaStore.Audio.Albums.CONTENT_TYPE == intent.type ->
                    handleAlbumIntent(intent)

                MediaStore.Audio.Artists.CONTENT_TYPE == intent.type ->
                    handleArtistIntent(intent)

                else -> false
            }

            if (handled) setIntent(Intent())
        }
    }

    private suspend fun handleSearchIntent(intent: Intent): Boolean {
        val extras = intent.extras ?: return false
        val songs = getSongs(extras)
        if (MusicPlayerRemote.shuffleMode == MusicService.SHUFFLE_MODE_SHUFFLE) {
            MusicPlayerRemote.openAndShuffleQueue(songs, true)
        } else {
            MusicPlayerRemote.openQueue(songs, 0, true)
        }
        return true
    }

    private suspend fun handleUriIntent(uri: Uri): Boolean {
        MusicPlayerRemote.playFromUri(this, uri)
        return true
    }

    private suspend fun handlePlaylistIntent(intent: Intent): Boolean {
        val id = parseLongFromIntent(intent, "playlistId", "playlist")
        if (id >= 0L) {
            val position = intent.getIntExtra("position", 0)
            val songs = PlaylistSongsLoader.getPlaylistSongList(get(), id)
            MusicPlayerRemote.openQueue(songs, position, true)
            return true
        }
        return false
    }

    private suspend fun handleAlbumIntent(intent: Intent): Boolean {
        val id = parseLongFromIntent(intent, "albumId", "album")
        if (id >= 0L) {
            val position = intent.getIntExtra("position", 0)
            val songs = withContext(IO) { libraryViewModel.albumById(id).songs }
            MusicPlayerRemote.openQueue(songs, position, true)
            return true
        }
        return false
    }

    private suspend fun handleArtistIntent(intent: Intent): Boolean {
        val id = parseLongFromIntent(intent, "artistId", "artist")
        if (id >= 0L) {
            val position = intent.getIntExtra("position", 0)
            val songs = withContext(IO) { libraryViewModel.artistById(id).songs }
            MusicPlayerRemote.openQueue(songs, position, true)
            return true
        }
        return false
    }

    private fun parseLongFromIntent(intent: Intent, longKey: String, stringKey: String): Long {
        var id = intent.getLongExtra(longKey, -1)
        if (id < 0) {
            intent.getStringExtra(stringKey)?.let {
                try {
                    id = it.toLong()
                } catch (e: NumberFormatException) {
                    logE(e)
                }
            }
        }
        return id
    }

    override fun showMiniPlayer(expand: Boolean) {
        slidingPanel.isVisible = expand
    }
}
