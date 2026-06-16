package dev.cherrypizza.mcserver.bootstrap

import dev.cherrypizza.mcserver.bootstrap.utils.coroutine.BukkitMainDispatcher
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.env.Environment
import io.micronaut.runtime.Micronaut
import jakarta.inject.Singleton
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.slf4j.LoggerFactory

/**
 * Дефолтный generic main-класс серверного плагина: поднимает Micronaut
 * `ApplicationContext`, сканируя ВЕСЬ classpath плагина. Любые бины потребителя
 * (eureka/redisson/auth/...), оказавшиеся на classpath shaded-jar'а, подхватываются
 * автоматически — поэтому тулкиту не нужно ничего знать про конкретный сервер.
 */
class MicronautServerPlugin : JavaPlugin() {
    companion object {
        private var instance: MicronautServerPlugin? = null
        private var applicationContext: ApplicationContext? = null
    }

    override fun onEnable() {
        instance = this
        try {
            applicationContext = Micronaut.build()
                .classLoader(classLoader)
                .environments(Environment.TEST) // Костыль чтоб при ошибке запуска не вызывался System.exit()
                .eagerInitSingletons(true)
                .banner(false)
                .overrideConfigLocations("file:server-config")
                .start()
        } catch (ex: Exception) {
            LoggerFactory.getLogger(MicronautServerPlugin::class.java)
                .error("Failed to start plugin, shutdown server", ex)
            Bukkit.shutdown()
        }
    }

    override fun onDisable() {
        // applicationContext.stop() запускает все @PreDestroy. Среди них есть suspend-логика,
        // которая через withContext(BukkitMainDispatcher) хочет вернуться на main тред.
        // К моменту onDisable плагин уже isEnabled=false → Bukkit scheduler не принимает
        // задачи. Поэтому stop() уносим на IO, а main-тред крутит drain loop в runShutdown,
        // обрабатывая main-таргетные continuation'ы из своей очереди.
        val ctx = applicationContext ?: return
        val dispatcher = ctx.getBean(BukkitMainDispatcher::class.java)
        dispatcher.runShutdown {
            ctx.stop()
        }
    }

    @Factory
    class MicronautServerPluginFactory {
        @Singleton
        fun serverPlugin(): MicronautServerPlugin = instance!!
    }
}
