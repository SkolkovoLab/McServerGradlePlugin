package dev.cherrypizza.mcserver.bootstrap

import io.micronaut.context.annotation.Secondary
import jakarta.inject.Singleton
import java.net.InetAddress

/**
 * Дефолтный generic-провайдер id ноды: env `SERVER_ID`, иначе hostname.
 *
 * Помечен [@Secondary] — потребитель (например, eureka-интеграция) легко перекрывает
 * его своим `@Singleton`-бином без конфликтов.
 */
@Singleton
@Secondary
class EnvNodeIdProvider : NodeIdProvider {
    override val id: String =
        System.getenv("SERVER_ID")?.takeIf { it.isNotBlank() }
            ?: InetAddress.getLocalHost().hostName
}
