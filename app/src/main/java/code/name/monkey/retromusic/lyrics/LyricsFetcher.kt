package code.name.monkey.retromusic.lyrics

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Response

data class LyricsResponse(
    val plainLyrics: String?,
    val syncedLyrics: String?
)

interface LrcLibApi {
    @GET("get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") artistName: String,
        @Query("album_name") albumName: String,
        @Query("duration") duration: Int
    ): Response<LyricsResponse>
}

object LyricsFetcher {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "RetroMusic/1.0 (https://github.com/effinmr/EffinMusic)")
                .build()
            chain.proceed(request)
        }
        .build()

    private val api: LrcLibApi = Retrofit.Builder()
        .baseUrl("https://lrclib.net/api/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()
        .create(LrcLibApi::class.java)

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): LyricsResponse? {
        return try {
            val response = api.getLyrics(
                trackName = title,
                artistName = artist,
                albumName = album,
                duration = (durationMs / 1000).toInt()
            )
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }
}
