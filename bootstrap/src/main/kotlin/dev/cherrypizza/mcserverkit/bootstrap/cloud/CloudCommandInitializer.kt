@file:Suppress("UNCHECKED_CAST")

package dev.cherrypizza.mcserverkit.bootstrap.cloud

import dev.cherrypizza.mcserverkit.bootstrap.CloudCommand
import dev.cherrypizza.mcserverkit.bootstrap.utils.kotlin.formatToPlayer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.incendo.cloud.CommandManager
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.bukkit.CloudBukkitCapabilities
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.paper.LegacyPaperCommandManager
import org.incendo.cloud.parser.ArgumentParser
import org.incendo.cloud.parser.ParserDescriptor
import org.slf4j.Logger

@Singleton
class CloudCommandInitializer(
    private val applicationContext: ApplicationContext,
    private val javaPlugin: JavaPlugin,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    @Bean
    @Singleton
    fun commandManager(
        interceptors: List<CloudCommandManagerInterceptor>,
        auto: List<AutoRegisterCloudCommandArg<*>>
    ): LegacyPaperCommandManager<CommandSender> {
        val manager = LegacyPaperCommandManager(
            javaPlugin,
            ExecutionCoordinator.simpleCoordinator(),
            SenderMapper.identity()
        )

        if (manager.hasCapability(CloudBukkitCapabilities.NATIVE_BRIGADIER)) {
            // Register Brigadier mappings for rich completions
            manager.registerBrigadier();
        } else if (manager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            // Use Paper async completions API (see Javadoc for why we don't use this with Brigadier)
            manager.registerAsynchronousCompletions();
        }
        manager.exceptionController().clearHandlers()
        manager.exceptionController().registerHandler(Exception::class.java) {
            logger.error(
                "Player {} has unhandled exception on command {}",
                it.context().sender().name,
                it.context().command().toString(),
                it.exception()
            )
            it.context().sender().sendMessage(it.exception().formatToPlayer())
        }

        for (interceptor in interceptors) {
            interceptor.intercept(manager)
        }
        for (arg in auto) {
            val descriptor = ParserDescriptor.of(
                arg as ArgumentParser<CommandSender, Any>,
                arg.type as Class<Any>
            )
            val name = arg.name
            if (name == null)
                manager.parserRegistry().registerParser(descriptor)
            else
                manager.parserRegistry().registerNamedParser(name, descriptor)
        }


        return manager
    }

    @Bean
    @Singleton
    fun cloudAnnotationParser(
        manager: LegacyPaperCommandManager<CommandSender>,
        interceptors: List<CloudCommandAnnotationParserInterceptor>
    ): AnnotationParser<*> {
        manager as CommandManager<CommandSender>
        val parser = AnnotationParser(manager, CommandSender::class.java)
            .installCoroutineSupport(scope = scope)

        for (interceptor in interceptors) {
            interceptor.intercept(manager, parser)
        }

        val beans = applicationContext.getBeansOfType(
            Any::class.java,
            Qualifiers.byStereotype(CloudCommand::class.java)
        )
        parser.parse(beans)


        return parser
    }
}