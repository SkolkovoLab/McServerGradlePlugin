package dev.cherrypizza.mcserverkit.bootstrap

import jakarta.inject.Singleton

@Singleton
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudCommand()
