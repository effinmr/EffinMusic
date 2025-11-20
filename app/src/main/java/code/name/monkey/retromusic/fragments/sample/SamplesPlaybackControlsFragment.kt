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
package code.name.monkey.retromusic.fragments.sample

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import code.name.monkey.appthemehelper.util.ColorUtil
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentSamplesControlsBinding
import code.name.monkey.retromusic.db.PlaylistEntity
import code.name.monkey.retromusic.db.toSongEntity
import code.name.monkey.retromusic.extensions.*
import code.name.monkey.retromusic.fragments.LibraryViewModel
import code.name.monkey.retromusic.fragments.ReloadType
import code.name.monkey.retromusic.fragments.base.AbsPlayerControlsFragment
import code.name.monkey.retromusic.fragments.base.AbsPlayerFragment
import code.name.monkey.retromusic.fragments.base.goToAlbum
import code.name.monkey.retromusic.fragments.base.goToArtist
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.service.MusicService
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.color.MediaNotificationProcessor
import com.google.android.material.slider.Slider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/**
 * Created by hemanths on 20/09/17.
 */

class SamplesPlaybackControlsFragment :
    AbsPlayerControlsFragment(R.layout.fragment_samples_controls),
    PopupMenu.OnMenuItemClickListener {

    private val libraryViewModel: LibraryViewModel by activityViewModel()
    private var _binding: FragmentSamplesControlsBinding? = null
    private val binding get() = _binding!!

    private var individualArtists: List<String> = emptyList()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSamplesControlsBinding.bind(view)

        setUpMusicControllers()
        binding.title.isSelected = true
    }

    override fun setColor(color: MediaNotificationProcessor) {
        lastPlaybackControlsColor = color.primaryTextColor
        lastDisabledPlaybackControlsColor = ColorUtil.withAlpha(color.primaryTextColor, 0.3f)

        val tintList = ColorStateList.valueOf(color.primaryTextColor)
        binding.playerMenu.imageTintList = tintList
        binding.songFavourite.imageTintList = tintList
        binding.title.setTextColor(color.primaryTextColor)
        binding.text.setTextColor(color.secondaryTextColor)
    }

    override fun onServiceConnected() {
        updateSong()
    }

    private fun updateSong() {
        val song = MusicPlayerRemote.currentSong
        binding.title.text = song.title
        
        val artistName = song.artistName?.trim()
        
        val allArtists = if (!PreferenceUtil.fixYear) {
            listOfNotNull(song.albumArtist, song.artistName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        } else {
            song.artistNames?.split(",")?.map { it.trim() } ?: emptyList()
        }
            
        individualArtists = allArtists
        
        // Always display the full artist name string
        binding.text.text = allArtists
            .joinToString(", ")
        
        updateIsFavorite()
        (requireParentFragment() as? AbsPlayerFragment)?.setupTitleAndArtistClicks(binding.title, binding.text, individualArtists)
    }

    public override fun show() {
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateSong()
    }

    private fun setUpMusicControllers() {
        setupFavourite()
        setupMenu()
    }

    private fun setupMenu() {
        binding.playerMenu.setOnClickListener {
            val popupMenu = PopupMenu(requireContext(), it)
            popupMenu.setOnMenuItemClickListener(this)
            popupMenu.inflate(R.menu.menu_player)
            popupMenu.menu.findItem(R.id.action_toggle_favorite).isVisible = false
            popupMenu.menu.findItem(R.id.action_toggle_lyrics).isChecked = PreferenceUtil.showLyrics
            popupMenu.show()
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        return (parentFragment as SamplesFragment).onMenuItemClick(item!!)
    }

    private fun setupFavourite() {
        binding.songFavourite.setOnClickListener {
            toggleFavorite(MusicPlayerRemote.currentSong)
        }
    }

    override fun onFavoriteStateChanged() {
        updateIsFavorite(animate = true)
    }

    fun updateIsFavorite(animate: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            val isFavorite: Boolean =
                libraryViewModel.isSongFavorite(MusicPlayerRemote.currentSong.id)
            withContext(Dispatchers.Main) {
                val icon = if (animate && VersionUtils.hasMarshmallow()) {
                    if (isFavorite) R.drawable.avd_favorite else R.drawable.avd_unfavorite
                } else {
                    if (isFavorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border
                }
                val drawable = requireContext().getTintedDrawable(
                    icon,
                    Color.WHITE
                )
                binding.songFavourite.apply {
                    setImageDrawable(drawable)
                    if (drawable is AnimatedVectorDrawable) {
                        drawable.start()
                    }
                }
            }
        }
    }

    private fun toggleFavorite(song: Song) {
        lifecycleScope.launch(Dispatchers.IO) {
            val playlist: PlaylistEntity = libraryViewModel.favoritePlaylist()
            val songEntity = song.toSongEntity(playlist.playListId)
            val isFavorite = libraryViewModel.isFavoriteSong(songEntity).isNotEmpty()
            if (isFavorite) {
                libraryViewModel.removeSongFromPlaylist(songEntity)
            } else {
                libraryViewModel.insertSongs(listOf(song.toSongEntity(playlist.playListId)))
            }
            libraryViewModel.forceReload(ReloadType.Playlists)
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(MusicService.FAVORITE_STATE_CHANGED))
        }
    }

    fun onFavoriteToggled() {
        toggleFavorite(MusicPlayerRemote.currentSong)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
