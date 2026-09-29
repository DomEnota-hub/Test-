package ru.railbrake.calculator.core.assistant

import android.content.Context
import ru.railbrake.calculator.core.TechnicalFamily

/** Immutable per-family snapshots. A full catalog is built only when requested. */
object AssistantRuntime {
    private data class Scope(val family: TechnicalFamily?, val variantId: String?)
    private data class Snapshot(
        val documents: List<AssistantDocument>,
        val engine: AssistantEngine
    )

    private val lock = Any()
    private val core = linkedMapOf<Scope, Snapshot>()
    private val full = linkedMapOf<Scope, AssistantEngine>()

    /** Switching the day-to-day context releases indexes for the old choice. */
    fun activate(family: TechnicalFamily?, variantId: String?) = synchronized(lock) {
        val selected = Scope(family, variantId)
        core.keys.retainAll(setOf(selected))
        full.keys.retainAll(setOf(selected))
    }

    fun getOrCreateCore(context: Context, family: TechnicalFamily? = null,
                        variantId: String? = null): AssistantEngine =
        synchronized(lock) {
            val scope = Scope(family, variantId)
            full[scope] ?: core.getOrPut(scope) {
                val docs = AssistantContentLoader(context.applicationContext).loadCore(family, variantId)
                Snapshot(docs, AssistantEngine(InMemoryAssistantIndex(docs), family))
            }.engine
        }

    fun getOrCreateFull(context: Context, family: TechnicalFamily? = null,
                        variantId: String? = null): AssistantEngine =
        synchronized(lock) {
            val scope = Scope(family, variantId)
            val result = full.getOrPut(scope) {
                val docs = core.getOrPut(scope) {
                    val coreDocs = AssistantContentLoader(context.applicationContext).loadCore(family, variantId)
                    Snapshot(coreDocs, AssistantEngine(InMemoryAssistantIndex(coreDocs), family))
                }.documents
                val all = (docs + AssistantContentLoader(context.applicationContext)
                    .loadAdditionalCatalog(family)).distinctBy(AssistantDocument::key)
                AssistantEngine(InMemoryAssistantIndex(all), family)
            }
            core.remove(scope) // The full engine supersedes its smaller snapshot.
            while (full.size > 2) full.remove(full.keys.first())
            result
        }

    /** The unselected mode retains the original all-catalog behavior. */
    fun getOrCreate(context: Context): AssistantEngine = getOrCreateFull(context)
}
