package ru.railbrake.calculator.core.assistant

import android.content.Context

/**
 * Process-lifetime cache for the read-only assistant index.
 * App assets are immutable within one installed build, so rebuilding the index
 * on every return to the Home screen only wastes I/O and allocations.
 */
object AssistantRuntime {
    @Volatile
    private var cachedEngine: AssistantEngine? = null

    fun getOrCreate(context: Context): AssistantEngine {
        cachedEngine?.let { return it }

        return synchronized(this) {
            cachedEngine ?: run {
                val documents = AssistantContentLoader(context.applicationContext).load()
                AssistantEngine(InMemoryAssistantIndex(documents)).also { cachedEngine = it }
            }
        }
    }
}
