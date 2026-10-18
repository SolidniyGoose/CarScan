package com.carscan.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
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

    /** Последний релиз или null, если релизов ещё нет. Мобильный интернет в машине рвётся — несколько попыток. */
    suspend fun latestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        retrying {
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
    }

    /**
     * Скачивает APK во внутренний кэш приложения, сообщая прогресс 0..1.
     * При обрыве связи докачивает с того же места (HTTP Range); недокачанный файл в установщик не уходит.
     */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val target = File(dir, "CarScan-${release.version}.apk")
        dir.listFiles()?.filter { it != target }?.forEach { it.delete() } // загрузки других версий
        // Остаток от прошлой загрузки этой версии больше релиза — это не он, начинаем заново
        if (release.apkSizeBytes > 0 && target.length() > release.apkSizeBytes) target.delete()
        try {
            downloadWithResume(release.apkUrl, release.apkSizeBytes, target, "CarScan/${installedVersion()}", onProgress)
            target
        } catch (e: IOException) {
            throw e // недокачанный файл оставляем: «Обновить» ещё раз продолжит с того же места
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private suspend fun <T> retrying(block: () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                if (++attempt >= CHECK_ATTEMPTS) throw e
                delay(RETRY_DELAY_MS * attempt)
            }
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
        private const val CHECK_ATTEMPTS = 3
        private const val DOWNLOAD_ATTEMPTS = 6
        private const val RETRY_DELAY_MS = 2_000L

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

        /** Загрузка с докачкой: при обрыве — пауза и продолжение с размера уже скачанного файла. */
        internal suspend fun downloadWithResume(
            url: String,
            expectedSize: Long,
            target: File,
            userAgent: String,
            onProgress: (Float) -> Unit,
            attempts: Int = DOWNLOAD_ATTEMPTS,
            retryDelayMs: Long = RETRY_DELAY_MS,
        ) {
            var attempt = 0
            while (true) {
                coroutineContext.ensureActive()
                try {
                    val total = downloadPart(url, expectedSize, target, userAgent, onProgress)
                    if (total <= 0 || target.length() == total) return
                    if (target.length() > total) target.delete()
                    throw IOException("файл скачан не полностью: ${target.length()} из $total байт")
                } catch (e: IOException) {
                    if (++attempt >= attempts) throw e
                    delay(retryDelayMs * attempt)
                }
            }
        }

        /** Одна попытка: продолжает с размера уже скачанного файла. Возвращает ожидаемый полный размер. */
        private suspend fun downloadPart(url: String, expectedSize: Long, target: File, userAgent: String, onProgress: (Float) -> Unit): Long {
            val offset = target.length()
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", userAgent)
                if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
                val append = when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> true
                    HttpURLConnection.HTTP_OK -> false // сервер отдал файл целиком — начинаем заново
                    416 -> return offset // запрошено с конца файла: уже всё скачано
                    else -> throw IOException("Загрузка: HTTP $code")
                }
                val already = if (append) offset else 0L
                val total = connection.contentLengthLong.takeIf { it > 0 }?.plus(already) ?: expectedSize
                connection.inputStream.use { input ->
                    java.io.FileOutputStream(target, append).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = already
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
                return total
            } finally {
                connection.disconnect()
            }
        }
    }
}
