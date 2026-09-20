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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlin.time.TimeSource
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Incremental background lexer — Scintilla's IdleStyling / ActionDuration model.
 *
 * Strategy:
 * 1. On each edit, the caller calls [scheduleFrom] with the invalidation offset.
 * 2. Any running job is cancelled; a mutex serializes successive lexer jobs.
 * 3. A new coroutine on [Dispatchers.Default] processes chunks of [BUDGET_BYTES]
 *    per iteration, yielding between chunks so the Compose frame scheduler can
 *    interleave rendering.
 * 4. A current-revision chunk is published from the private buffer and [styleClock]
 *    is bumped; the UI observes this via a StateFlow
 *    and triggers recomposition only of affected visible [LineView] items.
 *
 * The per-chunk budget starts at [BUDGET_BYTES] and adapts using an EMA of actual
 * throughput (bytes styled per millisecond), targeting [BUDGET_MS] milliseconds
 * of work per chunk.
 */
class IdleLexer(
    private val styleBuffer: StyleBuffer,
    private val provider: LanguageModeProvider,
    private val onStyled: suspend () -> Unit, // called after each chunk — lets ViewModel update StateFlow
) {
    private var job: Job? = null
    private val lexerMutex = Mutex()
    @kotlin.concurrent.Volatile private var generation: Long = 0L
    private val publicationDispatcher: CoroutineDispatcher = runCatching {
        Dispatchers.Main.isDispatchNeeded(EmptyCoroutineContext)
        Dispatchers.Main
    }.getOrDefault(Dispatchers.Default)

    // Adaptive throttling (Scintilla's ActionDuration)
    private var bytesPerMs = 500.0   // starting estimate; calibrated each chunk

    companion object {
        private const val BUDGET_MS = 40L          // target wall-time per chunk
        private const val MIN_BUDGET_BYTES = 2_000 // never style fewer than this per chunk
        private const val MAX_BUDGET_BYTES = 500_000
    }

    /**
     * Cancel in-flight lexing and schedule immutable [textRevision] from [fromChar].
     * Safe to call from any coroutine (the launch happens inside [scope]).
     */
    fun scheduleFrom(fromChar: Int, textRevision: String, scope: CoroutineScope) {
        val revision = ++generation
        job?.cancel()
        job = scope.launch(Dispatchers.Default) {
            lexerMutex.withLock {
            // A private document and style array ensure concurrent edits cannot change
            // line offsets or expose half-tokenized styles to the renderer.
            val document = DocumentModel(textRevision)
            val styled = StyleBuffer(textRevision.length)
            var pos = fromChar.coerceIn(0, document.length)

            while (pos < document.length && isActive && revision == generation) {
                val budget = budgetBytes()
                val end    = minOf(pos + budget, document.length)

                val t0 = TimeSource.Monotonic.markNow()
                provider.tokenizeRangeIntoBuffer(document, pos, end, styled)
                val elapsed = t0.elapsedNow().inWholeMilliseconds.coerceAtLeast(1L)

                // Update EMA throughput estimate
                bytesPerMs = bytesPerMs * 0.85 + ((end - pos).toDouble() / elapsed) * 0.15

                // Only the tiny publication step runs on Main, serialized with edits.
                withContext(publicationDispatcher) {
                    if (revision == generation) {
                        styleBuffer.copyRangeFrom(styled, pos, end)
                        onStyled()
                    }
                }
                pos = end
                yield()     // allow Compose frame scheduling between chunks
            }
            }
        }
    }

    /** Cancel and wait for the current job to finish (call before dispose). */
    suspend fun cancelAndWait() {
        job?.cancelAndJoin()
        job = null
    }

    fun cancel() {
        generation++
        job?.cancel()
        job = null
    }

    private fun budgetBytes(): Int =
        (bytesPerMs * BUDGET_MS).toInt().coerceIn(MIN_BUDGET_BYTES, MAX_BUDGET_BYTES)
}
