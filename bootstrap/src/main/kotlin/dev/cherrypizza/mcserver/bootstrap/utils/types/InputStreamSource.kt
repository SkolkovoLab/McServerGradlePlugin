package dev.cherrypizza.mcserver.bootstrap.utils.types

import org.jetbrains.annotations.Blocking
import java.io.InputStream

/**
 * Вызов может быть БЛОКИРУЮЩИМ
 */
fun interface InputStreamSource {
    @Blocking
    operator fun invoke(): InputStream
}