package dev.cherrypizza.mcserverkit.bootstrap.event

import io.micronaut.context.ApplicationContext
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

@Singleton
class ListenerInitializer(
    private val applicationContext: ApplicationContext,
    private val javaPlugin: JavaPlugin,
) {
    @PostConstruct
    fun init() {
        val beans = applicationContext.getBeansOfType(Listener::class.java)

        beans.forEach {
            Bukkit.getPluginManager().registerEvents(it, javaPlugin)
        }
    }
}