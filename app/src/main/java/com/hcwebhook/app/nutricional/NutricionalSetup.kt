package com.hcwebhook.app.nutricional

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.health.connect.client.permission.HealthPermission
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hcwebhook.app.HCWebhookApplication
import com.hcwebhook.app.HealthDataType
import com.hcwebhook.app.PreferencesManager
import com.hcwebhook.app.ScheduledSyncManager
import com.hcwebhook.app.SyncMode
import com.hcwebhook.app.SyncWorker
import com.hcwebhook.app.WebhookConfig

/**
 * Tudo o que o HC Webhook deixava para a pessoa configurar à mão, já decidido para o
 * Nutricional: um destino só (o link pessoal), todos os dados que o Nutricional lê e envio
 * automático a cada hora.
 */
object NutricionalSetup {
    const val INTERVAL_MINUTES = 60

    /** Dias de histórico do primeiro envio (o limite que a Conexão Saúde libera sem pedir mais). */
    const val HISTORY_DAYS = 30

    private const val PREFS = "nutricional_prefs"
    private const val KEY_HISTORY_SENT = "history_sent"
    private const val KEY_PARTIAL_ACCEPTED = "partial_accepted"
    private const val KEY_LAST_REQUEST_AT = "last_request_at"

    /** Dados de ciclo e vida íntima não são pedidos nem enviados: o Nutricional não os usa. */
    private val EXCLUDED = setOf(
        HealthDataType.MENSTRUATION_FLOW,
        HealthDataType.MENSTRUATION_PERIOD,
        HealthDataType.INTERMENSTRUAL_BLEEDING,
        HealthDataType.OVULATION_TEST,
        HealthDataType.CERVICAL_MUCUS,
        HealthDataType.SEXUAL_ACTIVITY,
    )

    val TYPES: Set<HealthDataType> = HealthDataType.entries.filterNot { it in EXCLUDED }.toSet()

    /** Dados que nem todo aparelho tem: a falta deles não vira aviso de "dados não liberados". */
    private val OPTIONAL = setOf(HealthDataType.MINDFULNESS, HealthDataType.SKIN_TEMPERATURE)

    /** Permissões de leitura dos dados (sem as de segundo plano e de histórico). */
    fun dataPermissions(): Set<String> =
        TYPES.map { HealthPermission.getReadPermission(it.recordClass) }.toSet()

    /** As permissões que todo aparelho com a Conexão Saúde consegue dar. */
    fun expectedPermissions(): Set<String> =
        TYPES.filterNot { it in OPTIONAL }.map { HealthPermission.getReadPermission(it.recordClass) }.toSet()

    /** O link conectado, ou null. Um destino que não seja o Nutricional não conta. */
    fun currentUrl(context: Context): String? =
        PreferencesManager(context).getWebhookConfigs()
            .firstOrNull { it.isEnabled && NutricionalLink.parse(it.url) != null }
            ?.url

    fun connect(context: Context, url: String) {
        val app = context.applicationContext
        val prefs = PreferencesManager(app)
        val changed = currentUrl(app) != url
        prefs.setWebhookConfigs(listOf(WebhookConfig(url = url, name = "Nutricional")))
        prefs.setEnabledDataTypes(TYPES)
        prefs.setSyncMode(SyncMode.INTERVAL)
        prefs.setSyncIntervalMinutes(INTERVAL_MINUTES)
        prefs.setHasSeenOnboarding()
        if (changed) {
            // Conta nova: o histórico vai de novo, e os registros da conta anterior saem.
            store(app).edit()
                .putBoolean(KEY_HISTORY_SENT, false)
                .putBoolean(KEY_PARTIAL_ACCEPTED, false)
                .remove(KEY_LAST_REQUEST_AT)
                .apply()
            prefs.clearWebhookLogs()
        }
        ScheduledSyncManager(app).cancelAllAlarms()
        (app as? HCWebhookApplication)?.scheduleSyncWork()
    }

    fun disconnect(context: Context) {
        val app = context.applicationContext
        val prefs = PreferencesManager(app)
        prefs.setWebhookConfigs(emptyList())
        prefs.clearWebhookLogs()
        store(app).edit().clear().apply()
        WorkManager.getInstance(app).cancelUniqueWork(NutricionalSync.WORK)
        (app as? HCWebhookApplication)?.cancelSyncWork()
    }

    fun isHistorySent(context: Context): Boolean =
        store(context).getBoolean(KEY_HISTORY_SENT, false)

    fun markHistorySent(context: Context) {
        store(context).edit().putBoolean(KEY_HISTORY_SENT, true).apply()
    }

    /** A pessoa escolheu enviar só parte dos dados: o aviso de "dados não liberados" some. */
    fun isPartialAccepted(context: Context): Boolean =
        store(context).getBoolean(KEY_PARTIAL_ACCEPTED, false)

    fun setPartialAccepted(context: Context) {
        store(context).edit().putBoolean(KEY_PARTIAL_ACCEPTED, true).apply()
    }

    /** Quando a tela pediu um envio pela última vez (0 = nunca). */
    fun lastRequestAt(context: Context): Long = store(context).getLong(KEY_LAST_REQUEST_AT, 0L)

    fun markRequested(context: Context) {
        store(context).edit().putLong(KEY_LAST_REQUEST_AT, System.currentTimeMillis()).apply()
    }

    private fun store(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Envio pedido pela tela (primeiro envio, "Enviar agora" e "Reenviar"), pelo WorkManager. */
object NutricionalSync {
    const val WORK = "nutricional_envio"

    /**
     * [days] = 0 envia só o que mudou desde o último envio. Enquanto o histórico inicial não
     * tiver chegado ao Nutricional, o [SyncWorker] manda os últimos
     * [NutricionalSetup.HISTORY_DAYS] dias em todo envio.
     */
    fun start(context: Context, days: Int = 0, replace: Boolean = true) {
        val app = context.applicationContext
        NutricionalSetup.markRequested(app)
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(
                workDataOf(
                    SyncWorker.KEY_SYNC_TYPE to "manual",
                    SyncWorker.KEY_RANGE_DAYS to days,
                )
            )
            .build()
        // REPLACE: um envio que ficou esperando a internet voltar recomeça na hora.
        // KEEP (envio automático ao abrir o app): não interrompe um envio em andamento.
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(app).enqueueUniqueWork(WORK, policy, request)
    }
}

/** Link que chegou de fora e espera a confirmação da pessoa. Sobrevive a uma rotação de tela. */
object NutricionalState {
    val pendingLink = mutableStateOf<String?>(null)
    val invalidLink = mutableStateOf(false)

    fun offer(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        // Reaberto pela lista de recentes: o link antigo não é um pedido novo.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val data = intent.dataString ?: return
        if (!NutricionalLink.isDeepLink(data)) return
        val url = NutricionalLink.parse(data)
        if (url != null) {
            pendingLink.value = url
            invalidLink.value = false
        } else {
            invalidLink.value = true
        }
    }
}
