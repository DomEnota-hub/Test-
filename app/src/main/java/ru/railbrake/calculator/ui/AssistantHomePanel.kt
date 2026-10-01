package ru.railbrake.calculator.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.WorkingLocomotive
import ru.railbrake.calculator.core.assistant.AssistantConversation
import ru.railbrake.calculator.core.assistant.AssistantEngine
import ru.railbrake.calculator.core.assistant.AssistantEngineResult
import ru.railbrake.calculator.core.assistant.AssistantIntent
import ru.railbrake.calculator.core.assistant.AssistantPendingClarification
import ru.railbrake.calculator.core.assistant.AssistantParsedQuery
import ru.railbrake.calculator.core.assistant.AssistantQueryParser
import ru.railbrake.calculator.core.assistant.AssistantAmbiguity
import ru.railbrake.calculator.core.assistant.AssistantRuntime
import ru.railbrake.calculator.core.assistant.AssistantVoiceInput
import ru.railbrake.calculator.core.assistant.AssistantVoiceOutcome
import ru.railbrake.calculator.core.assistant.AssistantVoicePhase

private data class AssistantPanelEngineState(
    val engine: AssistantEngine? = null,
    val loadingFullCatalog: Boolean = false,
    val coreFailed: Boolean = false,
    val fullCatalogFailed: Boolean = false
)

@Composable
internal fun AssistantHomePanel(workingLocomotive: WorkingLocomotive? = null) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val workingFamily = workingLocomotive?.family
    val workingVariant = workingLocomotive?.variantId
    val engineState by produceState(
        initialValue = AssistantPanelEngineState(),
        key1 = appContext,
        key2 = workingLocomotive
    ) {
        val core = withContext(Dispatchers.IO) {
            runCatching { AssistantRuntime.getOrCreateCore(appContext, workingFamily, workingVariant) }
        }
        val coreEngine = core.getOrNull()
        if (coreEngine == null) {
            value = AssistantPanelEngineState(coreFailed = true)
            return@produceState
        }

        value = AssistantPanelEngineState(
            engine = coreEngine,
            loadingFullCatalog = true
        )

        val full = withContext(Dispatchers.IO) {
            runCatching { AssistantRuntime.getOrCreateFull(appContext, workingFamily, workingVariant) }
        }
        value = full.fold(
            onSuccess = { fullEngine -> AssistantPanelEngineState(engine = fullEngine) },
            onFailure = {
                AssistantPanelEngineState(
                    engine = coreEngine,
                    fullCatalogFailed = true
                )
            }
        )
    }
    val engine = engineState.engine

    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<AssistantEngineResult?>(null) }
    var pending by remember { mutableStateOf<AssistantPendingClarification?>(null) }
    var searchingOtherFamily by remember { mutableStateOf(false) }
    // A query may run against the small core while the technical catalog is
    // loading. Resolve a provisional miss once the full index becomes ready.
    LaunchedEffect(engine, engineState.loadingFullCatalog) {
        if (engine != null && !engineState.loadingFullCatalog &&
            result is AssistantEngineResult.NoResult && pending == null && query.isNotBlank() &&
            query == result?.parsedQuery?.rawText?.trim()) {
            result = engine.query(query)
        }
    }
    val voiceInput = remember(appContext) { AssistantVoiceInput(appContext) }
    val scope = rememberCoroutineScope()
    var lookupJob by remember { mutableStateOf<Job?>(null) }
    var voiceJob by remember { mutableStateOf<Job?>(null) }
    var voicePhase by remember { mutableStateOf<AssistantVoicePhase?>(null) }
    var voiceMessage by remember { mutableStateOf<String?>(null) }
    fun startVoice() {
        if (voiceJob?.isActive == true || engine == null) return
        voiceMessage = null
        voiceJob = scope.launch {
            try {
                when (val outcome = voiceInput.capture { phase ->
                    withContext(Dispatchers.Main) { voicePhase = phase }
                }) {
                    is AssistantVoiceOutcome.Transcript -> {
                        query = outcome.text
                        voiceMessage = "Проверьте распознанный текст и нажмите «Найти»."
                    }
                    AssistantVoiceOutcome.NoSpeech -> voiceMessage = "Речь не распознана. Повторите запись или введите вопрос."
                }
            } catch (_: CancellationException) {
                // The panel was closed while recording.
            } catch (_: Exception) {
                voiceMessage = "Не удалось распознать речь. Повторите запись или введите вопрос."
            } finally {
                voicePhase = null
                voiceJob = null
            }
        }
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoice() else voiceMessage = "Разрешите доступ к микрофону в настройках приложения или введите вопрос."
    }
    DisposableEffect(voiceInput) {
        onDispose {
            voiceInput.stop()
            val activeJob = voiceJob
            activeJob?.cancel()
            if (activeJob == null) voiceInput.close()
            else activeJob.invokeOnCompletion { voiceInput.close() }
        }
    }

    fun submit(text: String = query) {
        val currentEngine = engine ?: return
        val prepared = text.trim()
        if (prepared.isBlank()) return
        val normalized = AssistantQueryParser.normalize(prepared)
        if (AssistantQueryParser.hasConflictingSeries(prepared)) {
            voiceMessage = "Уточните одну серию: ВЛ80С, 2ЭС5К, 3ЭС5К, ЧМЭ3, ЧМЭ3Т или ЧМЭ3Э."
            return
        }
        voiceMessage = null
        val explicitFamily = AssistantQueryParser.parse(prepared).family
            ?: if (pending?.missingParameter == AssistantAmbiguity.SERIES_REQUIRED)
                AssistantQueryParser.familyFromAnswer(prepared) else pending?.context?.family
        val requestedFamily = explicitFamily ?: workingFamily
        val requestedVariant = WorkingLocomotive.explicitlyNamed(normalized)?.variantId
            ?: if (requestedFamily == workingFamily) workingVariant else null
        lookupJob?.cancel()
        if (requestedFamily != null && (requestedFamily != workingFamily || requestedVariant != workingVariant)) {
            searchingOtherFamily = true
            lookupJob = scope.launch {
                val alternate = withContext(Dispatchers.IO) {
                    runCatching { AssistantRuntime.getOrCreateFull(appContext, requestedFamily, requestedVariant) }
                }
                searchingOtherFamily = false
                alternate.fold(
                    onSuccess = { otherEngine ->
                        val turn = AssistantConversation.submit(otherEngine, prepared, pending)
                        result = turn.result
                        pending = turn.pending
                        query = if (turn.pending != null) "" else prepared
                    },
                    onFailure = { voiceMessage = "Не удалось загрузить каталог другой серии. Повторите запрос." }
                )
            }
        } else {
            searchingOtherFamily = false
            val turn = AssistantConversation.submit(currentEngine, prepared, pending)
            result = turn.result
            pending = turn.pending
            query = if (turn.pending != null) "" else prepared
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Помощник",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                "Спросите по материалам приложения. Голосовой ввод и поиск работают без интернета.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Рабочий локомотив: ${workingLocomotive?.title ?: "не выбран"}. " +
                    "Названная в вопросе другая серия откроется разово.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = engine != null,
                singleLine = false,
                minLines = 1,
                maxLines = 3,
                label = { Text("Спросить по приложению…") },
                placeholder = { Text("Введите вопрос") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() })
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { submit() },
                    enabled = engine != null && query.isNotBlank() && !searchingOtherFamily
                ) {
                    Text("Найти")
                }
                OutlinedButton(
                    onClick = {
                        if (voiceJob?.isActive == true) {
                            voiceInput.stop()
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            startVoice()
                        } else {
                            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    enabled = engine != null
                ) {
                    Text(if (voiceJob?.isActive == true) "Завершить запись" else "Голос")
                }
            }
            if (searchingOtherFamily) Text("Загружаю каталог названной серии…")
            if (voicePhase != null || voiceMessage != null) {
                Text(
                    voiceMessage ?: when (voicePhase) {
                        AssistantVoicePhase.PREPARING -> "Подготавливаю офлайн распознавание…"
                        AssistantVoicePhase.LISTENING -> "Слушаю. Говорите коротко; запись завершится после паузы."
                        AssistantVoicePhase.TRANSCRIBING -> "Распознаю речь на устройстве…"
                        null -> ""
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (pending != null) {
                OutlinedButton(onClick = { submit("отмена") }) {
                    Text("Отмена")
                }
            }

            when {
                engine == null && !engineState.coreFailed -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            "Подготавливаю диагностику и ОПП…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                engineState.coreFailed -> {
                    Text(
                        "Не удалось подготовить локальный индекс. Основные разделы приложения продолжают работать штатно.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                engineState.loadingFullCatalog -> {
                    Text(
                        "Диагностика и ОПП уже доступны. Остальные разделы догружаются…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                engineState.fullCatalogFailed -> {
                    Text(
                        "Диагностика и ОПП доступны. Полный справочник не удалось догрузить.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            result?.let { current ->
                ParsedQuerySummary(current.parsedQuery)
                when (current) {
                    is AssistantEngineResult.Matches -> {
                        Text(
                            "Лучшие совпадения",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        current.hits.forEachIndexed { index, hit ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        context.startActivity(
                                            AssistantResultActivity.intent(
                                                context = context,
                                                target = hit.document.target,
                                                viewed = WorkingLocomotive.explicitlyNamed(
                                                    current.parsedQuery.normalizedText
                                                )
                                            )
                                        )
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "${index + 1}. ${hit.document.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${familyLabel(hit.document.family)} · ${sectionLabel(hit.document.section)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val viewed = WorkingLocomotive.explicitlyNamed(current.parsedQuery.normalizedText)
                                    if (workingLocomotive != null && hit.document.family != null &&
                                        (hit.document.family != workingFamily ||
                                            (viewed != null && viewed != workingLocomotive))) {
                                        Text(
                                            "Другая серия · рабочий локомотив ${workingLocomotive?.title.orEmpty()}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (hit.document.summary.isNotBlank()) {
                                        Text(
                                            hit.document.summary,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 3
                                        )
                                    }
                                    if (hit.document.safetyCritical) {
                                        Text(
                                            "Требует открытия полной карточки перед применением.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Text(
                                        "Открыть →",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    is AssistantEngineResult.Clarify -> {
                        Text(
                            current.clarification.question,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        current.clarification.options.forEach { option ->
                            AssistChip(
                                onClick = {
                                    if (option.id == "OTHER") {
                                        query = ""
                                    } else {
                                        submit(option.label)
                                    }
                                },
                                label = { Text(option.label) }
                            )
                        }
                    }

                    is AssistantEngineResult.NoResult -> {
                        Text(
                            current.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParsedQuerySummary(parsed: AssistantParsedQuery) {
    val parts = buildList {
        add(intentLabel(parsed.intent))
        parsed.family?.let { add(familyLabel(it)) }
        if (parsed.failureModes.isNotEmpty()) {
            add(parsed.failureModes.joinToString(" / ") { mode -> mode.label })
        }
    }
    Text(
        "Распознано: ${parts.joinToString(" · ")}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun intentLabel(intent: AssistantIntent): String = when (intent) {
    AssistantIntent.FIND_TOPIC -> "Поиск"
    AssistantIntent.DEFINE_TERM -> "Справка"
    AssistantIntent.PROCEDURE -> "Процедура"
    AssistantIntent.TROUBLESHOOT -> "Диагностика"
    AssistantIntent.OPEN_SCHEME -> "Схема"
    AssistantIntent.ACCEPTANCE -> "Приёмка"
    AssistantIntent.SAFETY -> "ОПП"
}

private fun familyLabel(family: TechnicalFamily?): String = when (family) {
    TechnicalFamily.VL80S -> "ВЛ80С"
    TechnicalFamily.ERMAK -> "Ермак"
    TechnicalFamily.CHME3, TechnicalFamily.CHME3T, TechnicalFamily.CHME3E -> family.title
    null -> "Общий материал"
}

private fun sectionLabel(section: TechnicalSection?): String = when (section) {
    TechnicalSection.PROFILES -> "Профили"
    TechnicalSection.EQUIPMENT -> "Оборудование"
    TechnicalSection.KNOWLEDGE -> "Справочник"
    TechnicalSection.DIAGNOSTICS -> "Диагностика"
    TechnicalSection.ELECTRICAL -> "Электросхемы"
    TechnicalSection.PNEUMATIC -> "Пневматика"
    TechnicalSection.ACCEPTANCE -> "Приёмка"
    TechnicalSection.SYSTEMS -> "Системы"
    TechnicalSection.SAFETY -> "Охрана труда"
    null -> "Материал"
}
