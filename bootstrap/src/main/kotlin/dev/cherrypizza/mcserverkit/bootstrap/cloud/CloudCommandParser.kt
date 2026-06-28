@file:Suppress("unused")

package dev.cherrypizza.mcserverkit.bootstrap.cloud

import dev.cherrypizza.mcserverkit.bootstrap.CloudCommand
import io.micronaut.context.ApplicationContext
import io.micronaut.context.event.StartupEvent
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.event.annotation.EventListener
import org.incendo.cloud.annotations.AnnotationParser
import javax.inject.Singleton

@Singleton
class CloudCommandParser(
    private val context: ApplicationContext,
    private val parser: AnnotationParser<*>,
) {
    @EventListener
    fun run(event: StartupEvent) {
        val beans = context.getBeansOfType(
            Any::class.java,
            Qualifiers.byStereotype(CloudCommand::class.java)
        )
        parser.parse(beans)
    }
}