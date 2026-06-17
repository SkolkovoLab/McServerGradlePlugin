package dev.cherrypizza.mcserverkit.bootstrap.utils.coroutine

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.slf4j.LoggerFactory

@Factory
class CoroutineFactory {
    private val logger = LoggerFactory.getLogger("coroutine")

    /**
     * Глобальный scope с основным потоком по умолчанию
     */
    @Bean
    @Singleton
    fun scope(dispatcher: BukkitMainDispatcher) = CoroutineScope(
        SupervisorJob() +
                dispatcher +
                CoroutineExceptionHandler { _, t ->
                    logger.error("Uncaught in coroutine", t)
                }
    )
}