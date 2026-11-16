package com.hcwebhook.app.nutricional

import com.hcwebhook.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Aviso de versão nova. O app é instalado fora da loja, então não se atualiza sozinho: ele
 * pergunta ao Nutricional qual é a versão publicada e, se for mais nova, mostra um aviso com o
 * botão de baixar.
 *
 * O endereço do download é fixo no app. A resposta do servidor só diz o número da versão, nunca
 * de onde baixar.
 */
object UpdateChecker {
    const val INFO_URL = "https://${NutricionalLink.HOST}/android/nutricional-webhook.json"
    const val DOWNLOAD_URL = "https://${NutricionalLink.HOST}/android/nutricional-webhook.apk"

    private const val MAX_BODY_BYTES = 4096L
    private val VERSION = Regex("^\\d{1,5}(\\.\\d{1,5}){0,3}$")

    /** A versão publicada, quando é mais nova que a instalada; null em qualquer outro caso. */
    suspend fun newerVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
            val request = Request.Builder()
                .url(INFO_URL)
                .header("User-Agent", "NutricionalWebhook/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .build()
            val text = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.peekBody(MAX_BODY_BYTES).string() else null
            }
            val published = if (text != null) parseVersion(text) else null
            if (published != null && Versions.isNewer(published, BuildConfig.VERSION_NAME)) published else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Endereço do download. Leva a versão anunciada (`?v=1.2.3`) para que um cache no caminho não
     * entregue o arquivo da versão anterior logo depois de uma atualização.
     */
    fun downloadUrl(version: String?): String {
        val clean = version?.trim()?.removePrefix("v")
        return if (clean != null && VERSION.matches(clean)) "$DOWNLOAD_URL?v=$clean" else DOWNLOAD_URL
    }

    /** O campo `version` de `{"version":"1.2.3"}`, só quando tem cara de versão. */
    fun parseVersion(text: String): String? {
        val value = try {
            Json.parseToJsonElement(text).jsonObject["version"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
        val clean = value?.trim()?.removePrefix("v") ?: return null
        return if (VERSION.matches(clean)) clean else null
    }
}
