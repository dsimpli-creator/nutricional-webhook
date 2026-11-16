package com.hcwebhook.app.nutricional

import java.net.URI
import java.net.URLDecoder

/**
 * O link pessoal de envio do Nutricional, e nada além dele.
 *
 * O app recebe o link de fora (botão "Conectar" do site ou área de transferência), então só
 * aceita o formato exato: HTTPS, o endereço do Nutricional e o caminho `/api/health/<segredo>`.
 * Kotlin puro, sem Android, para poder ser testado na JVM.
 */
object NutricionalLink {
    const val HOST = "nutri.virachef.com.br"
    const val SITE = "https://$HOST/"

    /** `nutricional://conectar?u=<link pessoal codificado>` — o que o botão do site abre. */
    const val SCHEME = "nutricional"
    const val ACTION = "conectar"

    private const val MAX_LENGTH = 2048
    private val PATH = Regex("^/api/health/[A-Za-z0-9]{32,128}$")

    /** O link normalizado, ou null quando o texto não é um link de envio do Nutricional. */
    fun parse(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_LENGTH) return null
        val candidate = if (isDeepLink(text)) fromDeepLink(text) else text
        return candidate?.let { validate(it) }
    }

    fun isDeepLink(text: String): Boolean = text.trim().startsWith("$SCHEME://", ignoreCase = true)

    /** Para mostrar na tela e nos registros sem expor o segredo. */
    fun masked(url: String): String = url.replace(Regex("(/api/health/)[A-Za-z0-9]+"), "$1••••")

    private fun fromDeepLink(text: String): String? {
        val uri = try {
            URI(text)
        } catch (e: Exception) {
            return null
        }
        if (!ACTION.equals(uri.host, ignoreCase = true)) return null
        val query = uri.rawQuery ?: return null
        val value = query.split('&')
            .firstOrNull { it.startsWith("u=") }
            ?.substring(2)
            ?: return null
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: Exception) {
            null
        }
    }

    private fun validate(url: String): String? {
        if (url.length > MAX_LENGTH) return null
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return null
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.port != -1) return null
        if (uri.rawQuery != null || uri.rawFragment != null) return null
        if (!HOST.equals(uri.host, ignoreCase = true)) return null
        val path = uri.rawPath ?: return null
        if (!PATH.matches(path)) return null
        return "https://$HOST$path"
    }
}

/** Comparação de versões "1.2.3" para o aviso de atualização. */
object Versions {
    fun isNewer(remote: String, local: String): Boolean {
        val a = parts(remote) ?: return false
        val b = parts(local) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(version: String): List<Int>? {
        val clean = version.trim().removePrefix("v")
        if (!Regex("^\\d{1,5}(\\.\\d{1,5}){0,3}$").matches(clean)) return null
        return clean.split('.').map { it.toInt() }
    }
}
