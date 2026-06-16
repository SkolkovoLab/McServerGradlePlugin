package dev.cherrypizza.mcserver.bootstrap.utils.kotlin

import kotlinx.coroutines.Job
import java.io.File
import java.io.InputStream
import java.net.JarURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

fun InputStream.readText(): String {
    return String(this.readAllBytes(), StandardCharsets.UTF_8)
}

inline fun <T> Array<T>.forEachReversed(action: (T) -> Unit) {
    var index = lastIndex
    while (index >= 0) {
        action(this[index])
        index--
    }
}

val KClass<*>.firstGenericType get() = this.supertypes.first().arguments.first().type!!.classifier as KClass<*>

infix fun Class<*>.instanceof(other: Class<*>) = other.isAssignableFrom(this)

inline fun <T> Iterable<T>.singleOrNullIfEmpty(predicate: (T) -> Boolean): T? {
    var single: T? = null
    for (element in this) {
        if (!predicate(element)) continue
        if (single != null) throw IllegalArgumentException("Collection contains more than one matching element.")
        single = element
    }
    return single
}

@Suppress("UNCHECKED_CAST")
fun <T> T?.asOptional(): Optional<T> = Optional.ofNullable(this) as Optional<T>

fun jobNullIfInactive(job: Job? = null): ReadWriteProperty<Any?, Job?> = object : ReadWriteProperty<Any?, Job?> {
    private var currentValue: Job? = job
    override fun getValue(thisRef: Any?, property: KProperty<*>): Job? = currentValue?.takeIf { it.isActive }
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Job?) {
        if (currentValue == value) return
        currentValue?.cancel()
        currentValue = value
    }
}

fun <T> Queue<T>.pollForEach(action: (T)-> Unit) {
    while (true) {
        val element = this.poll() ?: break
        action(element)
    }
}

fun resources(url: URL, path: String): Sequence<String> {
    when (val protocol = url.protocol) {
        "jar" -> {
            val connection = url.openConnection() as JarURLConnection
            val entries = connection.jarFile.use { it.entries().toList() }
            return entries.asSequence()
                .filter { !it.isDirectory }
                .filter { it.name.substringAfterLast('.', "") != "class" }
                .map { it.name }
                .filter { it.startsWith(path) }
        }

        "file" -> {
            return File(url.toURI()).walk()
                .asSequence()
                .filter { it.isFile }
                .filter { it.extension != "class" }
                .map { path + it.toURI().toString().removePrefix(url.toString()) }
        }

        else -> throw IllegalArgumentException("Unknown protocol $protocol in url $url for path $path")
    }
}

fun <K, V: Any> MutableMap<K, V>.setOrRemoveIfNull(key: K, value: V?) {
    if (value == null) remove(key) else put(key, value)
}