package code.name.monkey.retromusic.fragments.player

import code.name.monkey.retromusic.adapter.album.AlbumCoverPagerAdapter
import code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment

class SamplesPlayerAlbumCoverFragment : PlayerAlbumCoverFragment() {

    override fun onPageSelected(position: Int) {
        currentPosition = position
        if (binding.viewPager.adapter != null) {
            (binding.viewPager.adapter as AlbumCoverPagerAdapter).receiveColor(
                colorReceiver,
                position
            )
        }
        if (position != MusicPlayerRemote.position) {
            MusicPlayerRemote.playSongAtFrom(position, 30000)
        }
    }
}
