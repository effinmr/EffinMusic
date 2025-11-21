package code.name.monkey.retromusic.util

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.edit
import code.name.monkey.retromusic.App
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.db.PlaylistEntity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.*

class CustomPlaylistImageUtil private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    suspend fun setCustomPlaylistImage(playlist: PlaylistEntity, uri: Uri) {
        val context = App.getContext()
        withContext(Dispatchers.IO) {
            runCatching {
                Glide.with(context)
                    .asBitmap()
                    .load(uri)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .skipMemoryCache(true)
                    .submit()
                    .get()
            }.onSuccess { bitmap ->
                saveImage(context, playlist, bitmap)
            }.onFailure {
                Toast.makeText(context, R.string.error_load_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveImage(context: Context, playlist: PlaylistEntity, bitmap: Bitmap) {
        val dir = File(context.filesDir, FOLDER_NAME)
        if (!dir.exists() && !dir.mkdirs()) return

        val file = File(dir, getFileName(playlist))
        var successful = false

        try {
            file.outputStream().buffered().use { bos ->
                successful = ImageUtil.resizeBitmap(bitmap, 2048)
                    .compress(Bitmap.CompressFormat.JPEG, 100, bos)
            }
        } catch (e: IOException) {
            Toast.makeText(context, e.toString(), Toast.LENGTH_LONG).show()
        }

        if (successful) {
            prefs.edit { putBoolean(getFileName(playlist), true) }
            context.contentResolver.notifyChange(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null
            )
        }
    }

    suspend fun resetCustomPlaylistImage(playlist: PlaylistEntity) {
        withContext(Dispatchers.IO) {
            prefs.edit { putBoolean(getFileName(playlist), false) }
            val file = getFile(playlist)
            if (file.exists()) file.delete()
        }
    }

    fun hasCustomPlaylistImage(playlist: PlaylistEntity): Boolean {
        return prefs.getBoolean(getFileName(playlist), false)
    }

    companion object {
        private const val PREFS_NAME = "custom_playlist_image"
        private const val FOLDER_NAME = "/custom_playlist_images/"

        private var instance: CustomPlaylistImageUtil? = null

        fun getInstance(context: Context): CustomPlaylistImageUtil {
            if (instance == null) {
                instance = CustomPlaylistImageUtil(context.applicationContext)
            }
            return instance!!
        }

        fun getFileName(playlist: PlaylistEntity): String {
            var name = playlist.playlistName
            name = name.replace("[^a-zA-Z0-9]".toRegex(), "_")
            return "#${playlist.playListId}#$name.jpeg"
        }

        fun getFile(playlist: PlaylistEntity): File {
            val dir = File(App.getContext().filesDir, FOLDER_NAME)
            return File(dir, getFileName(playlist))
        }
    }
}
