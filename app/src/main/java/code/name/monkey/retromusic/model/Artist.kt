/*
 * Copyright (c) 2019 Hemanth Savarala.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by
 *  the Free Software Foundation either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

package code.name.monkey.retromusic.model

import code.name.monkey.retromusic.helper.SortOrder
import code.name.monkey.retromusic.util.MusicUtil
import code.name.monkey.retromusic.util.PreferenceUtil
import java.text.Collator

data class Artist(
    val id: Long,
    val albums: List<Album>,
    val isAlbumArtist: Boolean = false,
    private var _name: String? = null // Internal mutable property for name
) {
    constructor(
        artistName: String,
        albums: List<Album>,
        isAlbumArtist: Boolean = false
    ) : this(albums[0].artistId, albums, isAlbumArtist) {
        _name = artistName
    }

    // New constructor for cases where we only have a name and no albums initially
    constructor(
        id: Long,
        artistName: String,
        isAlbumArtist: Boolean = false
    ) : this(id, emptyList(), isAlbumArtist) {
        _name = artistName
    }

    var name: String = _name ?: "-" // Use _name if available, otherwise default
        get() {
            val resolvedName = _name ?: if (isAlbumArtist) getAlbumArtistName() else getArtistName()
            return when {
                MusicUtil.isVariousArtists(resolvedName) ->
                    VARIOUS_ARTISTS_DISPLAY_NAME

                MusicUtil.isArtistNameUnknown(resolvedName) ->
                    UNKNOWN_ARTIST_DISPLAY_NAME

                else -> resolvedName!!
            }
        }

    val songCount: Int
        get() {
            var songCount = 0
            for (album in albums) {
                songCount += album.songCount
            }
            return songCount
        }

    val albumCount: Int
        get() = albums.size

    val songs: List<Song>
        get() = albums.flatMap { it.songs }

    val sortedSongs: List<Song>
        get() {
            val collator = Collator.getInstance()
            return songs.sortedWith(
                when (PreferenceUtil.artistDetailSongSortOrder) {
                    SortOrder.ArtistSongSortOrder.SONG_A_Z -> { o1, o2 ->
                        collator.compare(o1.title, o2.title)
                    }

                    SortOrder.ArtistSongSortOrder.SONG_Z_A -> { o1, o2 ->
                        collator.compare(o2.title, o1.title)
                    }

                    SortOrder.ArtistSongSortOrder.SONG_ALBUM -> { o1, o2 ->
                        val cmp = collator.compare(o1.albumName, o2.albumName)
                        if (cmp == 0) o1.trackNumber.compareTo(o2.trackNumber) else cmp
                    }

                    SortOrder.ArtistSongSortOrder.SONG_YEAR -> { o1, o2 ->
                        if (PreferenceUtil.fixYear) {
                            // Compare full YYYY-MM-DD strings
                            val y1 = o1.year ?: "0000-00-00"
                            val y2 = o2.year ?: "0000-00-00"
                            val cmp = y2.compareTo(y1)
                            if (cmp == 0) o1.trackNumber.compareTo(o2.trackNumber) else cmp
                        } else {
                            // Fallback to integer‑year sort
                            val year1 = o1.year?.toIntOrNull() ?: 0
                            val year2 = o2.year?.toIntOrNull() ?: 0
                            val yearComparison = year2.compareTo(year1)
                            val diff = year2.compareTo(year1)
                            if (diff == 0) o1.trackNumber.compareTo(o2.trackNumber) else diff
                        }
                    }

                    SortOrder.ArtistSongSortOrder.SONG_DURATION -> { o1, o2 ->
                        o1.duration.compareTo(
                            o2.duration
                        )
                    }

                    else -> {
                        throw IllegalArgumentException("invalid ${PreferenceUtil.artistDetailSongSortOrder}")
                    }
                })
        }

    val sortedAlbums: List<Album>
        get() {
            val collator = Collator.getInstance()
            return albums.sortedWith(
                when (PreferenceUtil.artistAlbumSortOrder) {
                    SortOrder.ArtistAlbumSortOrder.ALBUM_A_Z -> { o1, o2 ->
                        collator.compare(o1.title, o2.title)
                    }

                    SortOrder.ArtistAlbumSortOrder.ALBUM_Z_A -> { o1, o2 ->
                        collator.compare(o2.title, o1.title)
                    }

                    SortOrder.ArtistAlbumSortOrder.ALBUM_YEAR_ASC -> { o1, o2 ->
                        if (PreferenceUtil.fixYear) {
                            val y1 = o1.year ?: "0000-00-00"
                            val y2 = o2.year ?: "0000-00-00"
                            y1.compareTo(y2)
                        } else {
                            val year1 = o1.year?.toIntOrNull() ?: 0
                            val year2 = o2.year?.toIntOrNull() ?: 0
                            year1.compareTo(year2)
                        }
                    }

                    SortOrder.ArtistAlbumSortOrder.ALBUM_YEAR -> { o1, o2 ->
                        if (PreferenceUtil.fixYear) {
                            val y1 = o1.year ?: "0000-00-00"
                            val y2 = o2.year ?: "0000-00-00"
                            y2.compareTo(y1)
                        } else {
                            val year1 = o1.year?.toIntOrNull() ?: 0
                            val year2 = o2.year?.toIntOrNull() ?: 0
                            year2.compareTo(year1)
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("invalid ${PreferenceUtil.artistAlbumSortOrder}")
                    }
                })
        }

    fun safeGetFirstAlbum(): Album {
        return albums.firstOrNull() ?: Album.empty
    }

    private fun getArtistName(): String {
        return safeGetFirstAlbum().safeGetFirstSong().artistName
    }

    private fun getAlbumArtistName(): String? {
        return safeGetFirstAlbum().safeGetFirstSong().albumArtist
    }

    companion object {
        const val UNKNOWN_ARTIST_DISPLAY_NAME = "Unknown Artist"
        const val VARIOUS_ARTISTS_DISPLAY_NAME = "Various Artists"
        const val VARIOUS_ARTISTS_ID: Long = -2
        val empty = Artist(-1, emptyList(), false, UNKNOWN_ARTIST_DISPLAY_NAME) // Update empty to use new constructor
    }
}
