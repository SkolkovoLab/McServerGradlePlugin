package dev.cherrypizza.mcserverkit.bootstrap.event

import jakarta.inject.Singleton

@Singleton
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventListenerBean()
