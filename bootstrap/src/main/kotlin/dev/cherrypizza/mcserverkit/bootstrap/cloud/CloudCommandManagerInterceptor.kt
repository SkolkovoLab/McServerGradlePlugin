package dev.cherrypizza.mcserverkit.bootstrap.cloud

import org.incendo.cloud.CommandManager
import org.incendo.cloud.annotations.AnnotationParser

fun interface CloudCommandManagerInterceptor {
    fun intercept(manager: CommandManager<*>)
}