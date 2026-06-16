package dev.cherrypizza.mcserver.bootstrap.event

import jakarta.inject.Singleton

@Singleton
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventListenerBean()
