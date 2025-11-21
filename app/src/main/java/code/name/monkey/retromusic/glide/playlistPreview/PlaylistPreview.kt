package code.name.monkey.retromusic.glide.playlistPreview

import code.name.monkey.retromusic.db.PlaylistEntity
import code.name.monkey.retromusic.db.PlaylistWithSongs
import code.name.monkey.retromusic.db.toSongs
import code.name.monkey.retromusic.model.Song

class PlaylistPreview(val playlistWithSongs: PlaylistWithSongs) {

    val playlistEntity: PlaylistEntity get() = playlistWithSongs.playlistEntity
    val songs: List<Song> get() = playlistWithSongs.songs.toSongs()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlaylistPreview) return false

        if (other.playlistEntity.playListId != playlistEntity.playListId) return false

    // compare song IDs in order
        return songs.map { it.id } == other.songs.map { it.id }
    }

    override fun hashCode(): Int {
        var result = playlistEntity.playListId.hashCode()
        result = 31 * result + songs.map { it.id }.hashCode()
        return result
    }
}
