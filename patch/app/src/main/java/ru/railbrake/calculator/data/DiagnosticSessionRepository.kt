package ru.railbrake.calculator.data

import android.content.Context
import android.util.Base64

data class DiagnosticSessionRecord(
    val timestampMillis: Long,
    val profileId: String,
    val variantId: String,
    val scenarioId: String,
    val scenarioTitle: String,
    val severity: String,
    val report: String
)

/** Локальный журнал без облачной отправки. Служебные и персональные сведения пользователь вносит по своему усмотрению. */
class DiagnosticSessionRepository(context: Context) {
    private val prefs = context.getSharedPreferences("diagnostic_sessions", Context.MODE_PRIVATE)

    fun load(): List<DiagnosticSessionRecord> = prefs.getStringSet(KEY, emptySet()).orEmpty()
        .mapNotNull(::decode)
        .sortedByDescending { it.timestampMillis }

    fun add(record: DiagnosticSessionRecord) {
        val next = (load() + record)
            .distinctBy { it.timestampMillis }
            .take(MAX)
        prefs.edit().putStringSet(KEY, next.map(::encode).toSet()).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    private fun encode(record: DiagnosticSessionRecord) = listOf(
        record.timestampMillis,
        record.profileId,
        record.variantId,
        record.scenarioId,
        record.scenarioTitle,
        record.severity,
        record.report
    ).joinToString("|") { b64(it.toString()) }

    private fun decode(value: String): DiagnosticSessionRecord? = runCatching {
        val p = value.split('|').map(::unb64)
        if (p.size != 7) return null
        DiagnosticSessionRecord(p[0].toLong(), p[1], p[2], p[3], p[4], p[5], p[6])
    }.getOrNull()

    private fun b64(value: String) = Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    private fun unb64(value: String) = String(Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)

    companion object {
        private const val KEY = "records"
        private const val MAX = 100
    }
}
