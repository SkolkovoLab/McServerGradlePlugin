package dev.cherrypizza.mcserver.bootstrap.beans

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.InjectionPoint
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Factory
class LoggerBeanFactory {
    @Prototype
    @Suppress("MnInjectionPoints")
    fun logger(injectionPoint: InjectionPoint<*>): Logger {
        return LoggerFactory.getLogger(injectionPoint.declaringBean.declaringType.get())
    }
}