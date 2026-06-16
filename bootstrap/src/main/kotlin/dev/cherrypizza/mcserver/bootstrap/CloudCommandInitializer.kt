package dev.cherrypizza.mcserver.bootstrap

import dev.cherrypizza.mcserver.bootstrap.utils.kotlin.formatToPlayer
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.bukkit.CloudBukkitCapabilities
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.paper.LegacyPaperCommandManager
import org.slf4j.Logger

@Singleton
class CloudCommandInitializer(
    private val applicationContext: ApplicationContext,
    private val javaPlugin: JavaPlugin,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    @PostConstruct
    fun init() {
        val cloudCommandManager = LegacyPaperCommandManager(
            javaPlugin,
            ExecutionCoordinator.simpleCoordinator(),
            SenderMapper.identity()
        )

        if (cloudCommandManager.hasCapability(CloudBukkitCapabilities.BRIGADIER)) {
            // Register Brigadier mappings for rich completions
            cloudCommandManager.registerBrigadier();
        } else if (cloudCommandManager.hasCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)) {
            // Use Paper async completions API (see Javadoc for why we don't use this with Brigadier)
            cloudCommandManager.registerAsynchronousCompletions();
        }
        cloudCommandManager.exceptionController().clearHandlers()
        cloudCommandManager.exceptionController().registerHandler(Exception::class.java) {
            logger.error(
                "Player {} has unhandled exception on command {}",
                it.context().sender().name,
                it.context().command().toString(),
                it.exception()
            )
            it.context().sender().sendMessage(it.exception().formatToPlayer())
        }

        val annotationParser = AnnotationParser(cloudCommandManager, CommandSender::class.java)
            .installCoroutineSupport(scope = scope)

        val beans = applicationContext.getBeansOfType(
            Any::class.java,
            Qualifiers.byStereotype(CloudCommand::class.java)
        )
        annotationParser.parse(beans)
    }
}