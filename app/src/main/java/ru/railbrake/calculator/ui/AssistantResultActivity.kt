package ru.railbrake.calculator.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection
import ru.railbrake.calculator.core.assistant.AssistantTarget
import ru.railbrake.calculator.ui.theme.AccentPalette
import ru.railbrake.calculator.ui.theme.AppThemeMode
import ru.railbrake.calculator.ui.theme.RailBrakeTheme

class AssistantResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val preferences = remember {
                getSharedPreferences("calculation_inputs", Context.MODE_PRIVATE)
            }
            val palette = remember {
                runCatching {
                    AccentPalette.valueOf(
                        preferences.getString("accent_palette", AccentPalette.BLUE.name)
                            ?: AccentPalette.BLUE.name
                    )
                }.getOrDefault(AccentPalette.BLUE)
            }
            val themeMode = remember {
                runCatching {
                    AppThemeMode.valueOf(
                        preferences.getString("theme_mode", AppThemeMode.LIGHT.name)
                            ?: AppThemeMode.LIGHT.name
                    )
                }.getOrDefault(AppThemeMode.LIGHT)
            }

            RailBrakeTheme(palette = palette, themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ResultContent()
                }
            }
        }
    }

    @Composable
    private fun ResultContent() {
        val kind = intent.getStringExtra(EXTRA_KIND)
        val id = intent.getStringExtra(EXTRA_ID)

        if (kind.isNullOrBlank() || id.isNullOrBlank()) {
            InvalidTarget()
            return
        }

        when (kind) {
            KIND_VL80_DIAGNOSTIC,
            KIND_ERMAK_DIAGNOSTIC -> {
                LocomotiveDiagnosticsScreen(initialScenarioId = id)
            }

            KIND_KNOWLEDGE -> {
                KnowledgeBaseScreen(
                    initialArticleId = id,
                    sectionBackLabel = "Помощник",
                    onSectionBack = { finish() }
                )
            }

            KIND_TECHNICAL -> {
                val family = runCatching {
                    TechnicalFamily.valueOf(intent.getStringExtra(EXTRA_FAMILY).orEmpty())
                }.getOrNull()
                val section = runCatching {
                    TechnicalSection.valueOf(intent.getStringExtra(EXTRA_SECTION).orEmpty())
                }.getOrNull()

                if (family == null || section == null) {
                    InvalidTarget()
                    return
                }

                TechnicalCatalogScreen(
                    initialFamily = family,
                    initialSection = section,
                    sectionBackLabel = "Помощник",
                    onSectionBack = { finish() },
                    initialEntryId = id,
                    onOpenLegacyArticle = { articleId ->
                        startActivity(createIntent(this, AssistantTarget.Knowledge(articleId)))
                    },
                    onOpenDiagnosticScenario = { scenarioId, _, _ ->
                        val target = if (
                            family == TechnicalFamily.ERMAK || scenarioId.startsWith("ER-DIAG-")
                        ) {
                            AssistantTarget.ErmakDiagnostic(scenarioId)
                        } else {
                            AssistantTarget.Vl80Diagnostic(scenarioId)
                        }
                        startActivity(createIntent(this, target))
                    }
                )
            }

            else -> InvalidTarget()
        }
    }

    @Composable
    private fun InvalidTarget() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Не удалось открыть найденный материал",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Поисковый результат не содержит корректного адреса карточки.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = { finish() }) {
                Text("Назад")
            }
        }
    }

    companion object {
        private const val EXTRA_KIND = "assistant_kind"
        private const val EXTRA_ID = "assistant_id"
        private const val EXTRA_FAMILY = "assistant_family"
        private const val EXTRA_SECTION = "assistant_section"

        private const val KIND_TECHNICAL = "technical"
        private const val KIND_VL80_DIAGNOSTIC = "vl80_diagnostic"
        private const val KIND_ERMAK_DIAGNOSTIC = "ermak_diagnostic"
        private const val KIND_KNOWLEDGE = "knowledge"

        fun createIntent(context: Context, target: AssistantTarget): Intent =
            Intent(context, AssistantResultActivity::class.java).apply {
                when (target) {
                    is AssistantTarget.Technical -> {
                        putExtra(EXTRA_KIND, KIND_TECHNICAL)
                        putExtra(EXTRA_ID, target.entryId)
                        putExtra(EXTRA_FAMILY, target.family.name)
                        putExtra(EXTRA_SECTION, target.section.name)
                    }

                    is AssistantTarget.Vl80Diagnostic -> {
                        putExtra(EXTRA_KIND, KIND_VL80_DIAGNOSTIC)
                        putExtra(EXTRA_ID, target.scenarioId)
                    }

                    is AssistantTarget.ErmakDiagnostic -> {
                        putExtra(EXTRA_KIND, KIND_ERMAK_DIAGNOSTIC)
                        putExtra(EXTRA_ID, target.scenarioId)
                    }

                    is AssistantTarget.Knowledge -> {
                        putExtra(EXTRA_KIND, KIND_KNOWLEDGE)
                        putExtra(EXTRA_ID, target.articleId)
                    }
                }
            }

        fun intent(context: Context, target: AssistantTarget): Intent =
            createIntent(context, target)
    }
}
