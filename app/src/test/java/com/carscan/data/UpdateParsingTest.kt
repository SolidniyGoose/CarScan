package com.carscan.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateParsingTest {

    @Test
    fun `ответ GitHub releases-latest`() {
        val body = """
            {"tag_name":"v1.2.0","body":"Новые часы","published_at":"2026-10-11T10:00:00Z",
             "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":10},
                       {"name":"CarScan-v1.2.0.apk","browser_download_url":"https://github.com/SolidniyGoose/CarScan/releases/download/v1.2.0/CarScan-v1.2.0.apk","size":55000000}]}
        """.trimIndent()
        val release = UpdateRepository.parseRelease(body)!!
        assertEquals("1.2.0", release.version)
        assertEquals("Новые часы", release.notes)
        assertTrue(release.apkUrl.endsWith("CarScan-v1.2.0.apk"))
        assertEquals(55_000_000L, release.apkSizeBytes)
    }

    @Test
    fun `релиз без APK не считается обновлением`() {
        assertNull(UpdateRepository.parseRelease("""{"tag_name":"v2.0.0","assets":[]}"""))
    }

    @Test
    fun `сравнение версий по числам, а не строкам`() {
        assertTrue(UpdateRepository.isNewer("1.0.10", "1.0.9"))
        assertTrue(UpdateRepository.isNewer("1.1", "1.0.5"))
        assertFalse(UpdateRepository.isNewer("1.0.0", "1.0"))
        assertFalse(UpdateRepository.isNewer("1.0.0", "1.2.0"))
    }
}
