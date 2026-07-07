package code.name.monkey.retromusic.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import code.name.monkey.retromusic.BuildConfig
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.api.GitHubApiService
import code.name.monkey.retromusic.api.GitHubRelease
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.lifecycle.lifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object UpdateCheckerUtil {

    private const val GITHUB_OWNER = "dhruvitpokharna"
    private const val GITHUB_REPO = "EffinMusic"
    private const val GITHUB_BASE_URL = "https://api.github.com/"

    fun checkUpdate(context: Context, lifecycleScope: LifecycleCoroutineScope, lifecycleOwner: LifecycleOwner) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl(GITHUB_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val service = retrofit.create(GitHubApiService::class.java)
                val response = service.getLatestRelease(GITHUB_OWNER, GITHUB_REPO)

                if (response.isSuccessful) {
                    val latestRelease = response.body()
                    latestRelease?.let {
                        val latestVersionName = it.tagName.removePrefix("v")
                        val currentVersionName = BuildConfig.VERSION_NAME

                        if (compareVersions(latestVersionName, currentVersionName) > 0) {
                            withContext(Dispatchers.Main) {
                                showUpdateDialog(context, lifecycleOwner, currentVersionName, latestVersionName, it.body ?: "", it.htmlUrl)
                            }
                        }
                    }
                } else {
                    // Handle API error, e.g., log the error code
                    println("GitHub API Error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle network or other exceptions
            }
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val parts1 = version1.split(".").map { it.toInt() }
        val parts2 = version2.split(".").map { it.toInt() }

        val minLength = minOf(parts1.size, parts2.size)
        for (i in 0 until minLength) {
            if (parts1[i] != parts2[i]) {
                return parts1[i].compareTo(parts2[i])
            }
        }
        return parts1.size.compareTo(parts2.size)
    }

    private fun showUpdateDialog(context: Context, lifecycleOwner: LifecycleOwner, currentVersion: String, latestVersion: String, changelog: String, releaseUrl: String) {
        MaterialDialog(context).show {
            lifecycleOwner(lifecycleOwner)
            cornerRadius(res = R.dimen.m3_dialog_corner_size)
            title(R.string.new_update_available)
            message(text = context.getString(R.string.update_message, currentVersion, latestVersion, changelog))
            positiveButton(R.string.update) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
                context.startActivity(intent)
                it.dismiss()
            }
            negativeButton(R.string.cancel) {
                it.dismiss()
            }
        }
    }
}
