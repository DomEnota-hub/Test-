package ru.railbrake.calculator.core

import android.content.Context
import java.io.BufferedInputStream
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream
import org.json.JSONObject

internal object TechnicalAssetReader {
    fun json(context: Context, asset: String): JSONObject {
        val logicalName = asset.removeSuffix(".gz")
        val candidates = listOf(logicalName, "$logicalName.gz")
        var lastMissing: FileNotFoundException? = null

        candidates.forEach { candidate ->
            val raw = try {
                context.assets.open(candidate)
            } catch (error: FileNotFoundException) {
                lastMissing = error
                return@forEach
            }
            return raw.use { input ->
                val buffered = BufferedInputStream(input)
                buffered.mark(2)
                val firstByte = buffered.read()
                val secondByte = buffered.read()
                buffered.reset()
                val decoded = if (firstByte == 0x1f && secondByte == 0x8b) {
                    GZIPInputStream(buffered)
                } else {
                    buffered
                }
                decoded.bufferedReader(Charsets.UTF_8).use { reader -> JSONObject(reader.readText()) }
            }
        }

        throw FileNotFoundException(
            "Technical asset '$asset' is missing; tried ${candidates.joinToString()}"
        ).apply { lastMissing?.let { initCause(it) } }
    }
}
