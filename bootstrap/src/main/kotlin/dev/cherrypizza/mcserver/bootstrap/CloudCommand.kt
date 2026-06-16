package dev.cherrypizza.mcserver.bootstrap

import jakarta.inject.Singleton

@Singleton
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudCommand()
