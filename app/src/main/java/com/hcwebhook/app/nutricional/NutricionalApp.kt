package com.hcwebhook.app.nutricional

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hcwebhook.app.BatteryOptimizationHelper
import com.hcwebhook.app.BuildConfig
import com.hcwebhook.app.HealthConnectManager
import com.hcwebhook.app.MainActivity
import com.hcwebhook.app.PreferencesManager
import com.hcwebhook.app.WebhookLog
import com.hcwebhook.app.ui.Group
import com.hcwebhook.app.ui.Notice
import com.hcwebhook.app.ui.NoticeTone
import com.hcwebhook.app.ui.SettingRow
import com.hcwebhook.app.ui.SupportingText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * Nutricional Webhook: a única tela do app.
 *
 * Sem conexão, ela ensina a conectar (um toque no Nutricional). Conectado, mostra se está tudo
 * certo, um botão "Enviar agora" e os avisos do que falta, cada um com o botão que resolve.
 * Tudo o que o HC Webhook deixava para configurar é decidido em [NutricionalSetup].
 */

private const val PAGE_HOME = 0
private const val PAGE_LOGS = 1
private const val PAGE_ABOUT = 2

private const val MAX_LOGS = 30

/** Ao abrir o app, envia sozinho se o último envio (ou pedido) for mais antigo que isto. */
private const val AUTO_SEND_AFTER_MS = 10 * 60 * 1000L

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
private const val SOURCE_URL = "https://github.com/dsimpli-creator/nutricional-webhook"
private const val UPSTREAM_URL = "https://github.com/mcnaveen/health-connect-webhook"

/** O que a Conexão Saúde (Health Connect) deste aparelho permite agora. */
private data class HealthState(
    val loaded: Boolean = false,
    val available: Boolean = false,
    val updateRequired: Boolean = false,
    val granted: Set<String> = emptySet(),
    val backgroundAvailable: Boolean = false,
)

/** O que se sabe dos envios: o último que deu certo, os registros e o último pedido da tela. */
private data class SendState(
    val syncTime: Long? = null,
    val logs: List<WebhookLog> = emptyList(),
    val requestAt: Long = 0L,
    val historySent: Boolean = false,
)

private enum class Tone { Ok, Error, Neutral }

private class Status(
    val tone: Tone,
    val title: String,
    val body: String? = null,
    val icon: ImageVector? = null,
    val busy: Boolean = false,
)

@Composable
fun NutricionalApp(
    activity: MainActivity,
    permissionLauncher: ActivityResultLauncher<Set<String>>,
) {
    val app = activity.applicationContext
    val scope = rememberCoroutineScope()
    val dataPermissions = remember { NutricionalSetup.dataPermissions() }
    val expectedPermissions = remember { NutricionalSetup.expectedPermissions() }
    val backgroundPermission = HealthConnectManager.BACKGROUND_PERMISSION_STR

    var page by rememberSaveable { mutableStateOf(PAGE_HOME) }
    var url by remember { mutableStateOf(NutricionalSetup.currentUrl(app)) }
    var health by remember { mutableStateOf(HealthState()) }
    var send by remember { mutableStateOf(SendState()) }
    var batteryRestricted by remember { mutableStateOf(isBatteryRestricted(app)) }
    var partialAccepted by remember { mutableStateOf(NutricionalSetup.isPartialAccepted(app)) }
    var update by remember { mutableStateOf<String?>(null) }
    // Voltando da tela de permissões no meio da conexão: falta o primeiro envio e a bateria.
    var setupRunning by rememberSaveable { mutableStateOf(false) }
    // Quantas vezes a tela de permissões foi pedida. Depois de duas recusas o Android não a
    // mostra mais, então o app passa a abrir a Conexão Saúde.
    var requests by rememberSaveable { mutableStateOf(0) }
    var askBattery by rememberSaveable { mutableStateOf(false) }
    var confirmDisconnect by rememberSaveable { mutableStateOf(false) }

    val pendingLink = NutricionalState.pendingLink.value
    val invalidLink = NutricionalState.invalidLink.value

    val workFlow = remember {
        WorkManager.getInstance(app).getWorkInfosForUniqueWorkFlow(NutricionalSync.WORK)
    }
    val workInfos by workFlow.collectAsState(initial = emptyList<WorkInfo>())
    val workInfo = workInfos.firstOrNull()
    val workState = workInfo?.state
    val attempts = workInfo?.runAttemptCount ?: 0

    fun wanted(state: HealthState): Set<String> =
        if (state.backgroundAvailable) dataPermissions + backgroundPermission else dataPermissions

    fun hasData(state: HealthState): Boolean = state.granted.any { it in dataPermissions }

    /** Relê o estado. É chamado toda vez que o app volta para a frente. */
    fun refresh() {
        url = NutricionalSetup.currentUrl(app)
        batteryRestricted = isBatteryRestricted(app)
        val finishing = setupRunning
        setupRunning = false
        scope.launch {
            val loadedHealth = loadHealth(app)
            val loadedSend = loadSend(app)
            health = loadedHealth
            send = loadedSend
            if (NutricionalSetup.currentUrl(app) == null || !hasData(loadedHealth)) return@launch
            if (finishing) {
                // Fim da conexão: primeiro envio e o pedido para o Android não pausar o app.
                NutricionalSync.start(app)
                if (isBatteryRestricted(app)) askBattery = true
            } else if (isStale(loadedSend)) {
                // Abrir o app já resolve: se o último envio é antigo, envia de novo.
                NutricionalSync.start(app, replace = false)
            }
        }
    }

    fun askPermissions(permissions: Set<String>) {
        if (permissions.isEmpty()) return
        if (requests >= 2) {
            openHealthSettings(activity)
            return
        }
        requests += 1
        try {
            permissionLauncher.launch(permissions)
        } catch (e: Exception) {
            openHealthSettings(activity)
        }
    }

    fun connect(link: String) {
        NutricionalSetup.connect(app, link)
        NutricionalState.pendingLink.value = null
        url = link
        partialAccepted = false
        requests = 0
        page = PAGE_HOME
        scope.launch {
            val loaded = loadHealth(app)
            health = loaded
            send = loadSend(app)
            if (!loaded.available) return@launch
            val missing = wanted(loaded) - loaded.granted
            val needed = missing.filter { it in expectedPermissions || it == backgroundPermission }
            if (needed.isEmpty()) {
                NutricionalSync.start(app)
                if (isBatteryRestricted(app)) askBattery = true
            } else {
                // Uma tela só do Android para todos os dados e o envio com o app fechado.
                setupRunning = true
                askPermissions(missing)
            }
        }
    }

    fun paste() {
        val link = NutricionalLink.parse(clipboardText(activity))
        if (link != null) {
            NutricionalState.pendingLink.value = link
        } else {
            NutricionalState.invalidLink.value = true
        }
    }

    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        activity.lifecycle.addObserver(observer)
        activity.permissionStatusCallback = { _ -> refresh() }
        onDispose {
            activity.lifecycle.removeObserver(observer)
            activity.permissionStatusCallback = null
        }
    }

    LaunchedEffect(workState) { send = loadSend(app) }
    LaunchedEffect(Unit) { update = UpdateChecker.newerVersion() }

    BackHandler(enabled = page != PAGE_HOME) { page = PAGE_HOME }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val connectedUrl = url
            when {
                page == PAGE_LOGS -> LogsPage(logs = send.logs, onBack = { page = PAGE_HOME })

                page == PAGE_ABOUT -> AboutPage(
                    onBack = { page = PAGE_HOME },
                    onOpen = { address -> openUrl(activity, address) },
                )

                connectedUrl == null -> ConnectPage(
                    update = update,
                    onOpenSite = { openUrl(activity, NutricionalLink.SITE + "#conectar-android") },
                    onPaste = { paste() },
                    onAbout = { page = PAGE_ABOUT },
                    onDownload = { openUrl(activity, UpdateChecker.downloadUrl(update)) },
                )

                else -> {
                    val granted = hasData(health)
                    val sending = workState == WorkInfo.State.RUNNING ||
                        (workState == WorkInfo.State.ENQUEUED && attempts == 0)
                    val retrying = workState == WorkInfo.State.ENQUEUED && attempts > 0
                    val status = statusOf(
                        health = health,
                        granted = granted,
                        sending = sending,
                        retrying = retrying,
                        workFailed = workState == WorkInfo.State.FAILED,
                        send = send,
                    )
                    val missingExpected = expectedPermissions - health.granted

                    PageColumn {
                        Header(title = "Nutricional Webhook")
                        StatusCard(
                            status = status,
                            footer = if (health.available && granted) "Envio automático de hora em hora." else null,
                        )

                        when {
                            !health.loaded -> PrimaryButton(text = "Enviar agora", enabled = false, onClick = {})

                            !health.available -> PrimaryButton(
                                text = if (health.updateRequired) "Atualizar a Conexão Saúde" else "Instalar a Conexão Saúde",
                                icon = Icons.AutoMirrored.Filled.OpenInNew,
                                onClick = { openHealthConnectStore(activity) },
                            )

                            !granted -> PrimaryButton(
                                text = "Liberar dados",
                                icon = Icons.Filled.Lock,
                                onClick = {
                                    setupRunning = true
                                    askPermissions(wanted(health) - health.granted)
                                },
                            )

                            else -> PrimaryButton(
                                text = if (sending) "Enviando…" else "Enviar agora",
                                icon = Icons.Filled.Sync,
                                busy = sending,
                                onClick = {
                                    NutricionalSync.start(app)
                                    send = send.copy(requestAt = System.currentTimeMillis())
                                },
                            )
                        }

                        val newer = update
                        if (newer != null) {
                            Notice(
                                icon = Icons.Filled.Download,
                                title = "Versão $newer disponível",
                                body = "Baixe e instale por cima desta. A conexão continua valendo.",
                                action = "Baixar",
                                onAction = { openUrl(activity, UpdateChecker.downloadUrl(newer)) },
                            )
                        }
                        if (health.available && !granted && requests > 0) {
                            Notice(
                                icon = Icons.Filled.Info,
                                title = "A tela de permissões não abriu?",
                                body = "Abra a Conexão Saúde, toque em Permissões de apps, escolha " +
                                    "Nutricional Webhook e ligue Permitir tudo.",
                                action = "Abrir a Conexão Saúde",
                                onAction = { openHealthSettings(activity) },
                            )
                        }
                        if (granted && missingExpected.isNotEmpty() && !partialAccepted) {
                            Notice(
                                icon = Icons.Filled.Lock,
                                title = "Alguns dados não foram liberados",
                                body = "O Nutricional só recebe o que você permitir.",
                                action = "Liberar",
                                onAction = { askPermissions(dataPermissions - health.granted) },
                                secondaryAction = "Deixar assim",
                                onSecondaryAction = {
                                    NutricionalSetup.setPartialAccepted(app)
                                    partialAccepted = true
                                },
                            )
                        }
                        if (granted && health.backgroundAvailable && backgroundPermission !in health.granted) {
                            Notice(
                                icon = Icons.Filled.Schedule,
                                title = "Falta permitir o envio com o app fechado",
                                body = "Sem isso, os dados só são enviados quando você abre este app.",
                                tone = NoticeTone.Error,
                                action = "Permitir",
                                onAction = { askPermissions(setOf(backgroundPermission)) },
                            )
                        }
                        if (granted && !health.backgroundAvailable) {
                            val canUpdate = Build.VERSION.SDK_INT < 34
                            Notice(
                                icon = Icons.Filled.Info,
                                title = "Neste celular o envio é só com o app aberto",
                                body = "A Conexão Saúde deste aparelho não deixa ler os dados com o app " +
                                    "fechado. Abra este app de vez em quando: ele envia sozinho ao abrir.",
                                action = if (canUpdate) "Atualizar a Conexão Saúde" else null,
                                onAction = { openHealthConnectStore(activity) },
                            )
                        }
                        if (granted && batteryRestricted) {
                            Notice(
                                icon = Icons.Filled.Warning,
                                title = "O Android pode pausar o envio",
                                body = "Para enviar de hora em hora, deixe este app fora da economia de bateria.",
                                action = "Permitir",
                                onAction = { requestBatteryExemption(activity) },
                            )
                        }

                        Group(title = "Mais") {
                            if (granted) {
                                item {
                                    SettingRow(
                                        title = "Reenviar os últimos ${NutricionalSetup.HISTORY_DAYS} dias",
                                        summary = "Para quando faltar algum dia no Nutricional",
                                        icon = Icons.Filled.Refresh,
                                        onClick = {
                                            NutricionalSync.start(app, days = NutricionalSetup.HISTORY_DAYS)
                                            send = send.copy(requestAt = System.currentTimeMillis())
                                        },
                                    )
                                }
                            }
                            item {
                                SettingRow(
                                    title = "Registros de envio",
                                    icon = Icons.AutoMirrored.Filled.List,
                                    chevron = true,
                                    onClick = { page = PAGE_LOGS },
                                )
                            }
                            item {
                                SettingRow(
                                    title = "Abrir o Nutricional",
                                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                                    onClick = { openUrl(activity, NutricionalLink.SITE) },
                                )
                            }
                            item {
                                SettingRow(
                                    title = "Sobre o app",
                                    icon = Icons.Filled.Info,
                                    chevron = true,
                                    onClick = { page = PAGE_ABOUT },
                                )
                            }
                            item {
                                SettingRow(
                                    title = "Desconectar",
                                    summary = "Este celular para de enviar os dados",
                                    icon = Icons.Filled.Close,
                                    danger = true,
                                    onClick = { confirmDisconnect = true },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Um diálogo por vez: o link que chegou vem primeiro.
    when {
        pendingLink != null && pendingLink == url -> AlertDialog(
            onDismissRequest = { NutricionalState.pendingLink.value = null },
            title = { Text("Este celular já está conectado") },
            text = { Text("Não precisa fazer mais nada: os dados seguem indo para o seu Nutricional.") },
            confirmButton = {
                TextButton(onClick = { NutricionalState.pendingLink.value = null }) { Text("OK") }
            },
        )

        pendingLink != null -> AlertDialog(
            onDismissRequest = { NutricionalState.pendingLink.value = null },
            title = { Text("Conectar ao Nutricional?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Os dados de saúde deste celular vão para a sua conta em ${NutricionalLink.HOST}.")
                    Text("Só confirme se foi você quem tocou em Conectar este celular no Nutricional.")
                    if (url != null) {
                        Text("Este celular já envia para outra conta. Ao confirmar, ele passa a enviar para a nova.")
                    }
                }
            },
            confirmButton = { Button(onClick = { connect(pendingLink) }) { Text("Conectar") } },
            dismissButton = {
                TextButton(onClick = { NutricionalState.pendingLink.value = null }) { Text("Cancelar") }
            },
        )

        invalidLink -> AlertDialog(
            onDismissRequest = { NutricionalState.invalidLink.value = false },
            title = { Text("Esse não é o link do Nutricional") },
            text = {
                Text(
                    "No Nutricional, abra ⚙ Perfil e metas → Envio automático de saúde e toque em " +
                        "Conectar este celular. Ou copie o link de lá e volte aqui para colar."
                )
            },
            confirmButton = {
                TextButton(onClick = { NutricionalState.invalidLink.value = false }) { Text("Entendi") }
            },
        )

        askBattery -> AlertDialog(
            onDismissRequest = { askBattery = false },
            title = { Text("Último passo") },
            text = {
                Text(
                    "Para enviar de hora em hora com o app fechado, o Android precisa deixar este app " +
                        "funcionar em segundo plano. Na próxima tela, toque em Permitir."
                )
            },
            confirmButton = {
                Button(onClick = {
                    askBattery = false
                    requestBatteryExemption(activity)
                }) { Text("Permitir") }
            },
            dismissButton = { TextButton(onClick = { askBattery = false }) { Text("Agora não") } },
        )

        confirmDisconnect -> AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Desconectar este celular?") },
            text = {
                Text(
                    "O app para de enviar os dados. O que já chegou ao Nutricional continua lá. " +
                        "Para voltar, é só tocar em Conectar este celular no Nutricional."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    NutricionalSetup.disconnect(app)
                    url = null
                    send = SendState()
                    setupRunning = false
                    page = PAGE_HOME
                }) { Text("Desconectar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Estado
// ---------------------------------------------------------------------------------------------

private fun statusOf(
    health: HealthState,
    granted: Boolean,
    sending: Boolean,
    retrying: Boolean,
    workFailed: Boolean,
    send: SendState,
): Status {
    val syncTime = send.syncTime
    val lastLog = send.logs.firstOrNull()
    // Uma falha só conta enquanto for mais nova que o último envio que deu certo.
    val logFailed = lastLog != null && !lastLog.success && lastLog.timestamp > (syncTime ?: 0L)
    val staleFailure = workFailed && (syncTime ?: 0L) < send.requestAt

    return when {
        !health.loaded -> Status(Tone.Neutral, "Verificando…", busy = true)

        !health.available -> Status(
            tone = Tone.Error,
            title = if (health.updateRequired) "Atualize a Conexão Saúde" else "Falta a Conexão Saúde",
            body = "É na Conexão Saúde (Health Connect) que o Android guarda os dados de saúde. " +
                "Sem ela este app não tem o que enviar.",
            icon = Icons.Filled.Warning,
        )

        !granted -> Status(
            tone = Tone.Error,
            title = "Falta liberar os dados",
            body = "Toque em Liberar dados e escolha Permitir tudo.",
            icon = Icons.Filled.Lock,
        )

        sending -> Status(
            tone = Tone.Neutral,
            title = "Enviando…",
            body = if (send.historySent) null else
                "O primeiro envio leva os últimos ${NutricionalSetup.HISTORY_DAYS} dias e pode demorar um pouco.",
            busy = true,
        )

        retrying -> Status(
            tone = Tone.Neutral,
            title = "Tentando de novo",
            body = "Não deu para enviar agora. O app tenta sozinho em instantes.",
            icon = Icons.Filled.Schedule,
        )

        logFailed || staleFailure -> Status(
            tone = Tone.Error,
            title = "O último envio falhou",
            body = failureReason(if (logFailed) lastLog else null),
            icon = Icons.Filled.Warning,
        )

        syncTime != null -> Status(
            tone = Tone.Ok,
            title = "Tudo certo",
            body = "Último envio: ${whenText(syncTime)}.",
            icon = Icons.Filled.CheckCircle,
        )

        else -> Status(
            tone = Tone.Neutral,
            title = "Pronto para enviar",
            body = "Toque em Enviar agora para mandar os dados ao Nutricional.",
            icon = Icons.Filled.Sync,
        )
    }
}

/** O motivo de uma falha, em palavras de quem usa, a partir da resposta do Nutricional. */
private fun failureReason(log: WebhookLog?): String {
    if (log == null || log.success) return "Não foi possível ler ou enviar os dados. Tente de novo."
    val code = log.statusCode
        ?: return "Sem internet ou sem resposta do Nutricional. O app tenta de novo sozinho."
    return when {
        code == 404 -> "O Nutricional não reconheceu o link deste celular. Abra o Nutricional e " +
            "toque em Conectar este celular de novo."
        code == 429 -> "Muitos envios em pouco tempo. O app tenta de novo sozinho na próxima hora."
        code == 413 -> "O envio ficou grande demais para o Nutricional. Avise quem cuida do sistema."
        code == 422 -> "O Nutricional não entendeu os dados. Atualize este app e tente de novo."
        code >= 500 -> "O Nutricional está fora do ar agora. O app tenta de novo sozinho."
        else -> "O Nutricional recusou o envio (código $code)."
    }
}

private fun isStale(send: SendState): Boolean {
    val latest = maxOf(send.syncTime ?: 0L, send.requestAt)
    return System.currentTimeMillis() - latest > AUTO_SEND_AFTER_MS
}

private fun whenText(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val hour = moment.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (moment.toLocalDate()) {
        today -> "hoje às $hour"
        today.minusDays(1) -> "ontem às $hour"
        else -> moment.format(DateTimeFormatter.ofPattern("dd/MM")) + " às $hour"
    }
}

private suspend fun loadHealth(context: Context): HealthState = withContext(Dispatchers.IO) {
    try {
        val sdk = HealthConnectClient.getSdkStatus(context)
        if (sdk != HealthConnectClient.SDK_AVAILABLE) {
            HealthState(
                loaded = true,
                updateRequired = sdk == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
            )
        } else {
            val manager = HealthConnectManager(context)
            HealthState(
                loaded = true,
                available = true,
                granted = manager.getGrantedPermissions(),
                backgroundAvailable = manager.isBackgroundReadAvailable(),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        HealthState(loaded = true)
    }
}

private suspend fun loadSend(context: Context): SendState = withContext(Dispatchers.IO) {
    try {
        val prefs = PreferencesManager(context)
        SendState(
            syncTime = prefs.getLastSyncTime(),
            logs = prefs.getWebhookLogs().take(MAX_LOGS),
            requestAt = NutricionalSetup.lastRequestAt(context),
            historySent = NutricionalSetup.isHistorySent(context),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SendState()
    }
}

// ---------------------------------------------------------------------------------------------
// Telas do Android abertas pelo app
// ---------------------------------------------------------------------------------------------

private fun tryStart(activity: Activity, intent: Intent): Boolean = try {
    activity.startActivity(intent)
    true
} catch (e: Exception) {
    false
}

private fun openUrl(activity: Activity, address: String): Boolean =
    tryStart(activity, Intent(Intent.ACTION_VIEW, Uri.parse(address)))

private fun isBatteryRestricted(context: Context): Boolean = try {
    val power = context.getSystemService(PowerManager::class.java)
    power != null && !power.isIgnoringBatteryOptimizations(context.packageName)
} catch (e: Exception) {
    false
}

/** Pede ao Android para não pausar o app; se o pedido direto não existir, abre os ajustes. */
private fun requestBatteryExemption(activity: Activity) {
    if (tryStart(activity, BatteryOptimizationHelper.buildRequestExemptionIntent(activity))) return
    val oem = BatteryOptimizationHelper.buildOemDeepLinkIntent()
    if (oem != null && tryStart(activity, oem)) return
    tryStart(activity, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

/** As permissões deste app na Conexão Saúde; cada Android tem um caminho, tenta do mais direto. */
private fun openHealthSettings(activity: Activity) {
    val name = activity.packageName
    val candidates = listOf(
        Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
            .putExtra(Intent.EXTRA_PACKAGE_NAME, name),
        Intent("androidx.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
            .putExtra(Intent.EXTRA_PACKAGE_NAME, name)
            .setPackage(HEALTH_CONNECT_PACKAGE),
        Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"),
        Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
    )
    for (intent in candidates) {
        if (tryStart(activity, intent)) return
    }
}

private fun openHealthConnectStore(activity: Activity) {
    val store = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"),
    ).setPackage("com.android.vending")
    if (tryStart(activity, store)) return
    openUrl(activity, "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")
}

private fun clipboardText(context: Context): String? = try {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip
    if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context)?.toString() else null
} catch (e: Exception) {
    null
}

// ---------------------------------------------------------------------------------------------
// Páginas
// ---------------------------------------------------------------------------------------------

@Composable
private fun ConnectPage(
    update: String?,
    onOpenSite: () -> Unit,
    onPaste: () -> Unit,
    onAbout: () -> Unit,
    onDownload: () -> Unit,
) {
    PageColumn {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NutricionalLogo(dp = 88.dp)
            Text(
                "Nutricional Webhook",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "Envia os dados de saúde deste celular para o seu Nutricional, sozinho, de hora em hora.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Step(1, "Toque em Abrir o Nutricional.")
                Step(2, "Entre na sua conta, se pedir.")
                Step(3, "Toque em Conectar este celular.")
            }
        }

        PrimaryButton(
            text = "Abrir o Nutricional",
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = onOpenSite,
        )
        OutlinedButton(
            onClick = onPaste,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text("Já copiei meu link: colar") }

        SupportingText(
            "Se a tela de conectar não abrir sozinha: no Nutricional, ⚙ Perfil e metas → " +
                "Envio automático de saúde."
        )

        if (update != null) {
            Notice(
                icon = Icons.Filled.Download,
                title = "Versão $update disponível",
                body = "Baixe e instale por cima desta.",
                action = "Baixar",
                onAction = onDownload,
            )
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onAbout, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sobre o app") }
        }
    }
}

@Composable
private fun LogsPage(logs: List<WebhookLog>, onBack: () -> Unit) {
    PageColumn {
        Header(title = "Registros de envio", onBack = onBack)
        if (logs.isEmpty()) {
            SupportingText("Nenhum envio ainda.")
        } else {
            Group {
                logs.forEach { log ->
                    item {
                        SettingRow(
                            title = whenText(log.timestamp).replaceFirstChar { it.uppercase() },
                            summary = logSummary(log),
                            icon = if (log.success) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        )
                    }
                }
            }
            SupportingText("Os $MAX_LOGS envios mais recentes. Ficam só neste celular.")
        }
    }
}

private fun logSummary(log: WebhookLog): String {
    if (!log.success) return "Falhou. " + failureReason(log)
    val count = log.recordCount
    return if (count != null) "Enviado · $count registros" else "Enviado"
}

@Composable
private fun AboutPage(onBack: () -> Unit, onOpen: (String) -> Unit) {
    PageColumn {
        Header(title = "Sobre o app", onBack = onBack)
        Group {
            item { SettingRow(title = "Versão", value = BuildConfig.VERSION_NAME) }
            item {
                SettingRow(
                    title = "Código-fonte",
                    summary = "github.com/dsimpli-creator/nutricional-webhook",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { onOpen(SOURCE_URL) },
                )
            }
            item {
                SettingRow(
                    title = "Projeto original",
                    summary = "HC Webhook, de MC.Naveen",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = { onOpen(UPSTREAM_URL) },
                )
            }
        }
        Group(title = "Privacidade") {
            item {
                Paragraph(
                    "Este app lê os dados de saúde que você liberar na Conexão Saúde e os envia " +
                        "somente para a sua conta no Nutricional (${NutricionalLink.HOST}), por " +
                        "conexão segura. Não tem anúncios nem rastreadores. Dados de ciclo menstrual " +
                        "e de vida íntima não são lidos."
                )
            }
        }
        Group(title = "Licença") {
            item {
                Paragraph(
                    "Nutricional Webhook é uma adaptação do HC Webhook (Health Connect Webhook), " +
                        "© MC.Naveen, distribuída sob a licença GNU AGPL-3.0 com o adendo do " +
                        "projeto original. O código-fonte desta adaptação é público, no endereço acima."
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Peças
// ---------------------------------------------------------------------------------------------

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun Header(title: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Spacer(Modifier.width(4.dp))
        } else {
            NutricionalLogo(dp = 36.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun StatusCard(status: Status, footer: String?) {
    val colors = MaterialTheme.colorScheme
    val container = when (status.tone) {
        Tone.Ok -> colors.primaryContainer
        Tone.Error -> colors.errorContainer
        Tone.Neutral -> colors.surfaceContainerHigh
    }
    val content = when (status.tone) {
        Tone.Ok -> colors.onPrimaryContainer
        Tone.Error -> colors.onErrorContainer
        Tone.Neutral -> colors.onSurface
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val icon = status.icon
            if (status.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = content,
                    strokeWidth = 3.dp,
                )
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(status.title, style = MaterialTheme.typography.headlineSmall)
            val body = status.body
            if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge)
            if (footer != null) Text(footer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** O botão principal da tela: um só por estado, largo e alto o bastante para o polegar. */
@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(number.toString(), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(16.dp),
    )
}

/** A marca do Nutricional (quadrado verde com os dois anéis), desenhada para não pesar no app. */
@Composable
private fun NutricionalLogo(dp: Dp) {
    Canvas(Modifier.size(dp)) {
        val side = size.width
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0xFF23875E), Color(0xFF1A6A49))),
            cornerRadius = CornerRadius(side * 0.24f, side * 0.24f),
        )
        ring(radius = side * 0.29f, sweep = 256f, color = Color.White)
        ring(radius = side * 0.16f, sweep = 230.6f, color = Color(0xFFF29A4A))
    }
}

private fun DrawScope.ring(radius: Float, sweep: Float, color: Color) {
    val stroke = Stroke(width = size.width * 0.07f, cap = StrokeCap.Round)
    val corner = Offset(center.x - radius, center.y - radius)
    val box = Size(radius * 2f, radius * 2f)
    drawArc(
        color = Color.White.copy(alpha = 0.2f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = corner,
        size = box,
        style = stroke,
    )
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = corner,
        size = box,
        style = stroke,
    )
}
