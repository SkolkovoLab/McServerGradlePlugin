package dev.cherrypizza.mcserverkit.bootstrap.utils.coroutine

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun CoroutineScope.fork(
    name: String? = null,
    context: CoroutineContext = EmptyCoroutineContext
): CoroutineScope {
    // 1. Начинаем с базового контекста родителя
    var combinedContext = this.coroutineContext + SupervisorJob(this.coroutineContext[Job])

    // 2. Если передано имя, добавляем логирование и имя корутины
    if (name != null) {
        val logger = LoggerFactory.getLogger("Coroutine-$name")
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            logger.error("Unhandled exception in coroutine", throwable)
        }
        combinedContext = combinedContext + exceptionHandler + CoroutineName(name)
    }

    // 3. В самом конце накатываем кастомный контекст (чтобы он мог перетереть Dispatcher, если нужно)
    return CoroutineScope(combinedContext + context)
}