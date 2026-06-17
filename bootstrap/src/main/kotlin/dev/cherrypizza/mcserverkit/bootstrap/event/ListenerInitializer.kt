package dev.cherrypizza.mcserverkit.bootstrap.event

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
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
        val beans = applicationContext.getBeansOfType(
            Any::class.java,
            Qualifiers.byStereotype(EventListenerBean::class.java)
        )
        beans.forEach {
            Bukkit.getPluginManager().registerEvents(it as Listener, javaPlugin)
        }
    }
}