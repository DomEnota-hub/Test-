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
    private var generation = 0L

    /** Switching the day-to-day context releases indexes for the old choice. */
    fun activate(family: TechnicalFamily?, variantId: String?) = synchronized(lock) {
        generation++
        val selected = Scope(family, variantId)
        core.keys.retainAll(setOf(selected))
        full.keys.retainAll(setOf(selected))
    }

    fun getOrCreateCore(context: Context, family: TechnicalFamily? = null,
                        variantId: String? = null): AssistantEngine {
        val scope = Scope(family, variantId)
        val started = synchronized(lock) {
            (full[scope] ?: core[scope]?.engine) to generation
        }
        started.first?.let { return it }
        val docs = AssistantContentLoader(context.applicationContext).loadCore(family, variantId)
        val built = AssistantEngine(InMemoryAssistantIndex(docs), family)
        return synchronized(lock) {
            full[scope] ?: core[scope]?.engine ?: built.also {
                if (generation == started.second) {
                    core[scope] = Snapshot(docs, built)
                    while (core.size > 2) core.remove(core.keys.first())
                }
            }
        }
    }

    fun getOrCreateFull(context: Context, family: TechnicalFamily? = null,
                        variantId: String? = null): AssistantEngine {
        val scope = Scope(family, variantId)
        val started = synchronized(lock) {
            Triple(full[scope], core[scope]?.documents, generation)
        }
        started.first?.let { return it }
        val loader = AssistantContentLoader(context.applicationContext)
        val docs = started.second ?: loader.loadCore(family, variantId)
        val all = (docs + loader.loadAdditionalCatalog(family)).distinctBy(AssistantDocument::key)
        val built = AssistantEngine(InMemoryAssistantIndex(all), family)
        return synchronized(lock) {
            full[scope] ?: built.also {
                // A selection made during loading must not resurrect the old index.
                if (generation == started.third) {
                    core.remove(scope)
                    full[scope] = built
                    while (full.size > 2) full.remove(full.keys.first())
                }
            }
        }
    }

    /** The unselected mode retains the original all-catalog behavior. */
    fun getOrCreate(context: Context): AssistantEngine = getOrCreateFull(context)
}
