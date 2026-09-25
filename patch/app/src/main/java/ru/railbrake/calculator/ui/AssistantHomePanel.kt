package ru.railbrake.calculator.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.assistant.AssistantClarificationOption
import ru.railbrake.calculator.core.assistant.AssistantEngine
import ru.railbrake.calculator.core.assistant.AssistantEngineResult
import ru.railbrake.calculator.core.assistant.AssistantIntent
import ru.railbrake.calculator.core.assistant.AssistantParsedQuery
import ru.railbrake.calculator.core.assistant.AssistantRuntime

@Composable
internal fun AssistantHomePanel() {
    val appContext = LocalContext.current.applicationContext
    val engineState by produceState<Result<AssistantEngine>?>(initialValue = null, appContext) {
        value = withContext(Dispatchers.IO) {
            runCatching { AssistantRuntime.getOrCreate(appContext) }
        }
    }
    val engine = engineState?.getOrNull()

    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<AssistantEngineResult?>(null) }

    fun submit(text: String = query) {
        val currentEngine = engine ?: return
        val prepared = text.trim()
        if (prepared.isBlank()) return
        query = prepared
        result = currentEngine.query(prepared)
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
                "Спросите по материалам приложения. Поиск работает локально и пока только в текстовом режиме.",
                style = MaterialTheme.typography.bodyMedium,
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
                placeholder = { Text("Например: на ВЛ80С ГВ не включается") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() })
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { submit() },
                    enabled = engine != null && query.isNotBlank()
                ) {
                    Text("Найти")
                }
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Голос — позже")
                }
            }

            when {
                engineState == null -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Text(
                            "Подготавливаю локальный индекс…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                engineState?.isFailure == true -> {
                    Text(
                        "Не удалось подготовить локальный индекс. Основные разделы приложения продолжают работать штатно.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                result == null -> {
                    Text("Примеры", style = MaterialTheme.typography.labelLarge)
                    listOf(
                        "на ВЛ80С ГВ не включается",
                        "покажи ГВ на схеме 3ЭС5К",
                        "обморожение"
                    ).forEach { example ->
                        AssistChip(
                            onClick = { submit(example) },
                            label = { Text(example) }
                        )
                    }
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
                            Card(modifier = Modifier.fillMaxWidth()) {
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
                                    val next = clarificationQuery(current.parsedQuery, option)
                                    if (next != null) {
                                        submit(next)
                                    } else {
                                        query = "${current.parsedQuery.rawText.trim()} "
                                        result = null
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
    }
    Text(
        "Распознано: ${parts.joinToString(" · ")}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

private fun clarificationQuery(
    parsed: AssistantParsedQuery,
    option: AssistantClarificationOption
): String? = when (option.id) {
    "VL80S" -> "${parsed.rawText} ВЛ80С"
    "ERMAK" -> "${parsed.rawText} Ермак"
    "diagnostics" -> "диагностика ${parsed.rawText}"
    "reference" -> "описание ${parsed.rawText}"
    "scheme" -> "${parsed.rawText} на схеме"
    "procedure" -> "порядок ${parsed.rawText}"
    "FULL" -> "полная проба тормозов"
    "SHORT" -> "сокращенная проба тормозов"
    "TECH" -> "технологическая проба тормозов"
    "MAIN_BREAKER" -> "${parsed.rawText} главный выключатель"
    "COMPRESSOR" -> "${parsed.rawText} компрессор"
    else -> null
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
