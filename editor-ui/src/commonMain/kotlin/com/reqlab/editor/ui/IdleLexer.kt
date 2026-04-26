package com.reqlab.editor.ui

import com.reqlab.editor.core.DocumentModel
import com.reqlab.editor.core.LanguageModeProvider
import com.reqlab.editor.core.StyleBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.yield
import kotlin.time.TimeSource

/**
 * Incremental background lexer — Scintilla's IdleStyling / ActionDuration model.
 *
 * Strategy:
 * 1. On each edit, the caller calls [scheduleFrom] with the invalidation offset.
 * 2. Any running job is cancelled.
 * 3. A new coroutine on [Dispatchers.Default] processes chunks of [BUDGET_BYTES]
 *    per iteration, yielding between chunks so the Compose frame scheduler can
 *    interleave rendering.
 * 4. [styleClock] is bumped after each chunk; the UI observes this via a StateFlow
 *    and triggers recomposition only of affected visible [LineView] items.
 *
 * The per-chunk budget starts at [BUDGET_BYTES] and adapts using an EMA of actual
 * throughput (bytes styled per millisecond), targeting [BUDGET_MS] milliseconds
 * of work per chunk.
 */
class IdleLexer(
    private val document: DocumentModel,
    private val styleBuffer: StyleBuffer,
    private val provider: LanguageModeProvider,
    private val onStyled: suspend () -> Unit, // called after each chunk — lets ViewModel update StateFlow
) {
    private var job: Job? = null

    // Adaptive throttling (Scintilla's ActionDuration)
    private var bytesPerMs = 500.0   // starting estimate; calibrated each chunk

    companion object {
        private const val BUDGET_MS = 40L          // target wall-time per chunk
        private const val MIN_BUDGET_BYTES = 2_000 // never style fewer than this per chunk
        private const val MAX_BUDGET_BYTES = 500_000
    }

    /**
     * Cancel any in-flight lexing and schedule a new run starting from [fromChar].
     * Safe to call from any coroutine (the launch happens inside [scope]).
     */
    fun scheduleFrom(fromChar: Int, scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            var pos = fromChar.coerceAtLeast(0)
            styleBuffer.grow(document.length)

            while (pos < document.length && isActive) {
                val budget = budgetBytes()
                val end    = minOf(pos + budget, document.length)

                val t0 = TimeSource.Monotonic.markNow()
                provider.tokenizeRangeIntoBuffer(document, pos, end, styleBuffer)
                val elapsed = t0.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)

                // Update EMA throughput estimate
                bytesPerMs = bytesPerMs * 0.85 + ((end - pos).toDouble() / elapsed) * 0.15

                pos = end
                onStyled()  // notify ViewModel → update StateFlow styleClock
                yield()     // allow Compose frame scheduling between chunks
            }
        }
    }

    /** Cancel and wait for the current job to finish (call before dispose). */
    suspend fun cancelAndWait() {
        job?.cancelAndJoin()
        job = null
    }

    fun cancel() {
        job?.cancel()
        job = null
    }

    private fun budgetBytes(): Int =
        (bytesPerMs * BUDGET_MS).toInt().coerceIn(MIN_BUDGET_BYTES, MAX_BUDGET_BYTES)
}
