package dev.cherrypizza.mcserverkit.bootstrap.utils.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration

object CoroutineUtils {
    inline fun debounce(
        scope: CoroutineScope,
        crossinline suspendFunc: suspend () -> Unit,
        crossinline block: () -> Unit
    ): () -> Unit {

        val lock = Any()
        var job: Job? = null

        return {
            synchronized(lock) {
                job?.cancel()
                job = scope.launch {
                    suspendFunc()
                    block()
                }
            }
        }
    }

    inline fun throttle(
        scope: CoroutineScope,
        crossinline suspendFunc: suspend () -> Unit,
        crossinline block: suspend () -> Unit
    ): () -> Unit {

        val lock = Any()
        var job: Job? = null

        return {
            synchronized(lock) {
                if (job?.isActive != true) {
                    job = scope.launch {
                        suspendFunc()
                        block()
                    }
                }
            }
        }
    }

    inline fun debounce(
        scope: CoroutineScope,
        delay: Duration,
        crossinline block: () -> Unit
    ) = debounce(scope, { delay(delay) }, block)

    inline fun throttle(
        scope: CoroutineScope,
        delay: Duration,
        crossinline block: () -> Unit
    ) = throttle(scope, { delay(delay) }, block)
}