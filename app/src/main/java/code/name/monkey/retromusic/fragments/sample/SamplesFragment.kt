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

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentSamplesBinding
import code.name.monkey.retromusic.extensions.drawAboveSystemBars
import code.name.monkey.retromusic.extensions.hide
import code.name.monkey.retromusic.extensions.show
import code.name.monkey.retromusic.extensions.whichFragment
import code.name.monkey.retromusic.fragments.base.AbsPlayerFragment
import code.name.monkey.retromusic.fragments.base.goToArtist
import code.name.monkey.retromusic.fragments.player.CoverLyricsFragment
import code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.artistImageOptions
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.MusicProgressViewUpdateHelper
import code.name.monkey.retromusic.interfaces.IMiniPlayerExpanded
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.color.MediaNotificationProcessor
import com.bumptech.glide.Glide

class SamplesFragment : AbsPlayerFragment(R.layout.fragment_samples),
    MusicProgressViewUpdateHelper.Callback {
        
    private var _binding: FragmentSamplesBinding? = null
    private val binding get() = _binding!!
    private lateinit var progressViewUpdateHelper: MusicProgressViewUpdateHelper

    private var hasSkipped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressViewUpdateHelper = MusicProgressViewUpdateHelper(this)
    }

    override fun playerToolbar(): Toolbar {
        return binding.playerToolbar
    }

    private var lastColor: Int = 0
    override val paletteColor: Int
        get() = lastColor
    private lateinit var controlsFragment: SamplesPlaybackControlsFragment

    private fun setUpPlayerToolbar() {
        binding.playerToolbar.apply {
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSamplesBinding.bind(view)

        (requireActivity() as? IMiniPlayerExpanded)?.showMiniPlayer(false)

        libraryViewModel.getSongs().observe(viewLifecycleOwner) { songs ->
            if (songs.isNotEmpty()) {
                MusicPlayerRemote.openAndShuffleQueue(songs, false)
                MusicPlayerRemote.seekTo(30000)
                MusicPlayerRemote.resumePlaying()
            }
        }

        setUpSubFragments()
        setUpPlayerToolbar()
        setupArtist()
        binding.nextSong.isSelected = true
        binding.playbackControlsFragment.drawAboveSystemBars()
    }

    private fun setupArtist() {
        binding.artistImage.setOnClickListener {
            val song = MusicPlayerRemote.currentSong
            val ids = song.artistIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
            goToArtist(mainActivity, ids[0])
        }
    }

    private fun setUpSubFragments() {
        controlsFragment = whichFragment(R.id.playbackControlsFragment)
        val coverFragment: PlayerAlbumCoverFragment = whichFragment(R.id.playerAlbumCoverFragment)
        coverFragment.setCallbacks(this)
        coverFragment.removeSlideEffect()
    }

    override fun onShow() {
    }

    override fun onHide() {
    }

    override fun toolbarIconColor(): Int {
        return Color.WHITE
    }

    override fun onColorChanged(color: MediaNotificationProcessor) {
        lastColor = color.backgroundColor
        binding.mask.backgroundTintList = ColorStateList.valueOf(color.backgroundColor)
        controlsFragment.setColor(color)
        libraryViewModel.updateColor(color.backgroundColor)
        ToolbarContentTintHelper.colorizeToolbar(binding.playerToolbar, Color.WHITE, activity)
        binding.coverLyrics.getFragment<CoverLyricsFragment>().setColors(color)
    }

    override fun onFavoriteToggled() {
        toggleFavorite(MusicPlayerRemote.currentSong)
        controlsFragment.onFavoriteToggled()
    }

    override fun toggleFavorite(song: Song) {
        super.toggleFavorite(song)
        if (song.id == MusicPlayerRemote.currentSong.id) {
            updateIsFavorite()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateArtistImage()
        updateLabel()
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateArtistImage()
        updateLabel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as? IMiniPlayerExpanded)?.showMiniPlayer(false)
        MusicPlayerRemote.clearQueue()
        _binding = null
    }

    private fun updateArtistImage() {
        val song = MusicPlayerRemote.currentSong
        val ids = song.artistIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
        if (ids.isEmpty()) {
            return
        }
        libraryViewModel.artist(ids[0])
            .observe(viewLifecycleOwner) { artist ->
                if (artist.id != -1L) {
                    Glide.with(requireActivity())
                        .load(RetroGlideExtension.getArtistModel(artist))
                        .artistImageOptions(artist)
                        .into(binding.artistImage)
                }

            }
    }

    override fun onUpdateProgressViews(progress: Int, total: Int) {
        if (progress < 30000) {
            MusicPlayerRemote.playNextSongFrom(30000)
            return
        }
        
        if (!hasSkipped && progress >= 60000) {
            MusicPlayerRemote.playNextSongFrom(30000)
            hasSkipped = true
        }
        
        if (progress < 35000) { 
            hasSkipped = false
        }
    }

    override fun onResume() {
        super.onResume()
        progressViewUpdateHelper.start()
    }

    override fun onPause() {
        super.onPause()
        (requireActivity() as? IMiniPlayerExpanded)?.showMiniPlayer(true)
        MusicPlayerRemote.clearQueue()
        progressViewUpdateHelper.stop()
    }

    override fun onQueueChanged() {
        super.onQueueChanged()
        if (MusicPlayerRemote.playingQueue.isNotEmpty()) updateLabel()
    }

    private fun updateLabel() {
        if ((MusicPlayerRemote.playingQueue.size - 1) == (MusicPlayerRemote.position)) {
            binding.nextSongLabel.setText(R.string.last_song)
            binding.nextSong.hide()
        } else {
            val title = MusicPlayerRemote.playingQueue[MusicPlayerRemote.position + 1].title
            binding.nextSongLabel.setText(R.string.next_song)
            binding.nextSong.apply {
                text = title
                show()
            }
        }
    }
}
