package com.carscan.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** Релиз на GitHub с APK-файлом. */
data class AppRelease(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
    val publishedAt: String,
)

/**
 * Обновления из GitHub Releases репозитория [REPOSITORY].
 * Релиз публикует GitHub Actions (.github/workflows/release.yml) при пуше тега вида v1.2.3.
 */
class UpdateRepository(private val context: Context) {

    @Suppress("DEPRECATION")
    fun installedVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0"

    /** Последний релиз или null, если релизов ещё нет. */
    suspend fun latestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        val connection = URL("https://api.github.com/repos/$REPOSITORY/releases/latest").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "CarScan/${installedVersion()}")
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> error("GitHub ответил HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Скачивает APK во внутренний кэш приложения, сообщая прогресс 0..1. */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // старые загрузки
        val target = File(dir, "CarScan-${release.version}.apk")
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "CarScan/${installedVersion()}")
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Загрузка: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSizeBytes
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Android ставит обновление только с той же подписью. Сборка из Android Studio подписана
     * отладочным ключом, а релиз — ключом из GitHub Actions: такой переход требует один раз удалить приложение.
     */
    fun sameSignature(apk: File): Boolean? = runCatching {
        val installed = signatures(context.packageManager.getPackageInfo(context.packageName, signatureFlags()))
        val downloaded = signatures(context.packageManager.getPackageArchiveInfo(apk.path, signatureFlags()) ?: return null)
        installed.isNotEmpty() && installed == downloaded
    }.getOrNull()

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            info.signatures
        }
        return raw.orEmpty().map { it.toCharsString() }.toSet()
    }

    companion object {
        const val REPOSITORY = "SolidniyGoose/CarScan"
        const val RELEASES_PAGE = "https://github.com/$REPOSITORY/releases"
        private const val UPDATE_DIR = "updates"
        private const val TIMEOUT_MS = 15_000

        /** Ответ GitHub API releases/latest → релиз с первым APK-файлом. */
        fun parseRelease(body: String): AppRelease? {
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
                return AppRelease(
                    version = json.optString("tag_name").removePrefix("v").removePrefix("V"),
                    notes = json.optString("body").trim(),
                    apkUrl = asset.getString("browser_download_url"),
                    apkSizeBytes = asset.optLong("size"),
                    publishedAt = json.optString("published_at"),
                )
            }
            return null
        }

        /** Сравнение версий вида 1.2.3: больше нуля — [a] новее [b]. */
        fun compareVersions(a: String, b: String): Int {
            val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
                if (d != 0) return d
            }
            return 0
        }

        fun isNewer(candidate: String, installed: String): Boolean = compareVersions(candidate, installed) > 0
    }
}
