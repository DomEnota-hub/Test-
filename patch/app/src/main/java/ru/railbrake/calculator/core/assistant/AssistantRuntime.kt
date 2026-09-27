package ru.railbrake.calculator.core.assistant

import android.content.Context

/**
 * Process-lifetime cache for the read-only assistant indexes.
 *
 * Core content (diagnostics, OPP, compact knowledge) is built first. The full
 * technical catalog can then replace it without making the assistant unusable
 * while large compressed assets are being parsed.
 */
object AssistantRuntime {
    @Volatile
    private var cachedCoreDocuments: List<AssistantDocument>? = null

    @Volatile
    private var cachedCoreEngine: AssistantEngine? = null

    @Volatile
    private var cachedFullEngine: AssistantEngine? = null

    private val coreLock = Any()
    private val fullLock = Any()

    fun getOrCreateCore(context: Context): AssistantEngine {
        cachedFullEngine?.let { return it }
        cachedCoreEngine?.let { return it }

        return synchronized(coreLock) {
            cachedFullEngine ?: cachedCoreEngine ?: run {
                val documents = cachedCoreDocuments
                    ?: AssistantContentLoader(context.applicationContext).loadCore()
                        .also { cachedCoreDocuments = it }
                AssistantEngine(InMemoryAssistantIndex(documents)).also { cachedCoreEngine = it }
            }
        }
    }

    fun getOrCreateFull(context: Context): AssistantEngine {
        cachedFullEngine?.let { return it }

        return synchronized(fullLock) {
            cachedFullEngine ?: run {
                val appContext = context.applicationContext
                val coreDocuments = cachedCoreDocuments ?: synchronized(coreLock) {
                    cachedCoreDocuments
                        ?: AssistantContentLoader(appContext).loadCore()
                            .also { cachedCoreDocuments = it }
                }
                val additional = AssistantContentLoader(appContext).loadAdditionalCatalog()
                val documents = (coreDocuments + additional).distinctBy(AssistantDocument::key)
                AssistantEngine(InMemoryAssistantIndex(documents)).also { cachedFullEngine = it }
            }
        }
    }

    /** Backwards-compatible entry point for callers that require the complete index. */
    fun getOrCreate(context: Context): AssistantEngine = getOrCreateFull(context)
}
