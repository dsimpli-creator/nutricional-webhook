package com.hcwebhook.app

import com.hcwebhook.app.nutricional.NutricionalLink
import com.hcwebhook.app.nutricional.UpdateChecker
import com.hcwebhook.app.nutricional.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class NutricionalLinkTest {

    // Segredo de mentira, com o mesmo formato do servidor (48 letras e números).
    private val token = "AbCdEfGh1234567890AbCdEfGh1234567890AbCdEfGh1234"
    private val link = "https://nutri.virachef.com.br/api/health/$token"

    @Test
    fun acceptsThePersonalLink() {
        assertEquals(link, NutricionalLink.parse(link))
        assertEquals(link, NutricionalLink.parse("  $link\n"))
        assertEquals(link, NutricionalLink.parse("HTTPS://NUTRI.VIRACHEF.COM.BR/api/health/$token"))
    }

    @Test
    fun acceptsTheConnectButtonLink() {
        val deep = "nutricional://conectar?u=" + URLEncoder.encode(link, "UTF-8")
        assertTrue(NutricionalLink.isDeepLink(deep))
        assertEquals(link, NutricionalLink.parse(deep))
        assertEquals(link, NutricionalLink.parse("nutricional://conectar?x=1&u=" + URLEncoder.encode(link, "UTF-8")))
    }

    @Test
    fun refusesOtherDestinations() {
        val bad = listOf(
            null,
            "",
            "texto qualquer",
            "http://nutri.virachef.com.br/api/health/$token",
            "https://example.com/api/health/$token",
            "https://nutri.virachef.com.br.example.com/api/health/$token",
            "https://nutri.virachef.com.br@example.com/api/health/$token",
            "https://user:pass@nutri.virachef.com.br/api/health/$token",
            "https://nutri.virachef.com.br:8443/api/health/$token",
            "https://nutri.virachef.com.br/api/health/$token?x=1",
            "https://nutri.virachef.com.br/api/health/$token#x",
            "https://nutri.virachef.com.br/api/health/$token/extra",
            "https://nutri.virachef.com.br/api/health/curto",
            "https://nutri.virachef.com.br/api/health/",
            "https://nutri.virachef.com.br/login",
            "https://nutri.virachef.com.br/api/health/../health/$token",
            "https://nutri.virachef.com.br/api/health/%41$token",
            "nutricional://conectar",
            "nutricional://conectar?u=",
            "nutricional://outra?u=" + URLEncoder.encode(link, "UTF-8"),
            "nutricional://conectar?u=" + URLEncoder.encode("https://example.com/api/health/$token", "UTF-8"),
            "nutricional://conectar?u=" + URLEncoder.encode("http://nutri.virachef.com.br/api/health/$token", "UTF-8"),
            "https://nutri.virachef.com.br/api/health/" + "a".repeat(3000),
        )
        bad.forEach { assertNull("deveria recusar: $it", NutricionalLink.parse(it)) }
    }

    @Test
    fun masksTheSecret() {
        val masked = NutricionalLink.masked(link)
        assertFalse(masked.contains(token))
        assertTrue(masked.startsWith("https://nutri.virachef.com.br/api/health/"))
    }

    @Test
    fun comparesVersions() {
        assertTrue(Versions.isNewer("1.0.1", "1.0.0"))
        assertTrue(Versions.isNewer("1.10.0", "1.9.32"))
        assertTrue(Versions.isNewer("v2.0.0", "1.99.99"))
        assertTrue(Versions.isNewer("1.1", "1.0.9"))
        assertFalse(Versions.isNewer("1.0.0", "1.0.0"))
        assertFalse(Versions.isNewer("1.0", "1.0.0"))
        assertFalse(Versions.isNewer("0.9.9", "1.0.0"))
        assertFalse(Versions.isNewer("", "1.0.0"))
        assertFalse(Versions.isNewer("1.0.0-beta", "0.1.0"))
        assertFalse(Versions.isNewer("<script>", "1.0.0"))
    }

    @Test
    fun readsThePublishedVersion() {
        assertEquals("1.2.3", UpdateChecker.parseVersion("{\"version\":\"1.2.3\"}"))
        assertEquals("1.2.3", UpdateChecker.parseVersion("{\"version\":\" v1.2.3 \",\"size\":123}"))
        val bad = listOf(
            "",
            "1.2.3",
            "[]",
            "{}",
            "{\"version\":null}",
            "{\"version\":{\"a\":1}}",
            "{\"version\":\"1.2.3-beta\"}",
            "{\"version\":\"https://example.com/app.apk\"}",
            "<html>manutenção</html>",
        )
        bad.forEach { assertNull("deveria recusar: $it", UpdateChecker.parseVersion(it)) }
    }

    @Test
    fun downloadAddressCarriesOnlyAVersionNumber() {
        val base = UpdateChecker.DOWNLOAD_URL
        assertEquals("$base?v=1.2.3", UpdateChecker.downloadUrl("1.2.3"))
        assertEquals("$base?v=1.2.3", UpdateChecker.downloadUrl(" v1.2.3 "))
        listOf(null, "", "1.2.3-beta", "1.2.3&x=1", "../outro.apk").forEach {
            assertEquals("deveria ignorar: $it", base, UpdateChecker.downloadUrl(it))
        }
    }
}
