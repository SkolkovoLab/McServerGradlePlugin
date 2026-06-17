package dev.cherrypizza.mcserverkit.bootstrap.utils.coroutine

import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

/**
 * Диспатчер, выполняющий корутину на главном треде Bukkit.
 *
 * Используется через `withContext(bukkitMainDispatcher) { ... }` — если код уже на main
 * треде, вызов бесплатный (isDispatchNeeded = false).
 *
 * Shutdown-режим: после `setEnabled(false)` Bukkit scheduler не принимает задачи и любой
 * `Bukkit.getScheduler().runTask(plugin, ...)` падает с `IllegalPluginAccessException`.
 * Чтобы корутины с `withContext(main) { ... withContext(IO) { ... } ... }` не ломались
 * на возврате из IO, мы при выключенном плагине отправляем main-таргетные continuation'ы
 * в [shutdownChannel], а main-тред в [runShutdown] вычитывает его через `for in channel`.
 * Точка входа — [dev.cherrypizza.mcserverkit.bootstrap.MicronautServerPlugin.onDisable], которая оборачивает
 * `applicationContext.stop()` в `runShutdown`.
 */
@Singleton
class BukkitMainDispatcher(
    private val plugin: JavaPlugin,
) : CoroutineDispatcher() {
    private val log = LoggerFactory.getLogger(javaClass)
    private val shutdownChannel = Channel<kotlinx.coroutines.Runnable>(Channel.UNLIMITED)

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !Bukkit.isPrimaryThread()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (plugin.isEnabled) {
            Bukkit.getScheduler().runTask(plugin, block)
            return
        }
        if (shutdownChannel.trySend(block).isSuccess) return

        // Плагин выключен и shutdown-канал не активен (или закрыт). Терять continuation
        // нельзя — повиснет корутина. Запускаем inline на текущем треде как последнюю
        // надежду; не должно случаться при штатном выключении.
        log.warn(
            "Main dispatch while plugin disabled and no shutdown channel — inline on {}",
            Thread.currentThread().name,
        )
        block.run()
    }

    /**
     * Запустить suspend-блок и его main-thread continuation'ы во время shutdown'а
     * плагина, когда Bukkit scheduler уже не принимает задачи.
     *
     * Должен вызываться с main треда (внутри `onDisable`). Внутри:
     *  - `runBlocking` блокирует main и крутит свой event loop;
     *  - drainer-корутина читает [shutdownChannel] и исполняет задачи на main треде;
     *  - сам `block` исполняется на отдельном `Dispatchers.IO`, поэтому любой его
     *    `withContext(this)` действительно требует диспатча и попадает в канал.
     */
    fun runShutdown(block: () -> Unit) {
        check(Bukkit.isPrimaryThread()) { "runShutdown must be called from main thread" }
        check(!plugin.isEnabled) { "runShutdown must be called only if plugin is disabled" }

        runBlocking {
            val drainer = launch {
                for (task in shutdownChannel) runTask(task)
            }
            val completable = CompletableFuture<Unit>()
            Thread(
                {
                    try {
                        block()
                    } finally {
                        completable.complete(Unit)
                    }
                },
                "CrShutdown"
            ).start()
            completable.await()

            shutdownChannel.close()

            drainer.join()
        }
    }

    private fun runTask(task: Runnable) {
        try {
            task.run()
        } catch (t: Throwable) {
            log.error("Main-thread task threw during shutdown drain", t)
        }
    }
}
