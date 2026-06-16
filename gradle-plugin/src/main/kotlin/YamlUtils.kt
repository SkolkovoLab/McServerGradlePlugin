import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.io.File
import java.nio.charset.StandardCharsets

object YamlUtils {
    val LOAD = Load(LoadSettings.builder().build())
    val DUMP = Dump(DumpSettings.builder().build())

    /**
     * Мержит N YAML-файлов слева направо: каждый следующий слой override'ит предыдущий.
     * Несуществующие файлы пропускаются (полезно для опциональных *.local.yml).
     * Требует минимум один существующий файл в [sources].
     */
    fun deepMergeYaml(writeTo: File, vararg sources: File) {
        val existing = sources.filter { it.exists() }
        require(existing.isNotEmpty()) { "deepMergeYaml: ни один из source-файлов не существует: ${sources.joinToString { it.path }}" }

        val merged = existing.fold<File, Any?>(null) { acc, file ->
            val loaded = LOAD.loadFromReader(file.reader(StandardCharsets.UTF_8))
            if (acc == null) loaded else deepMergeYaml(acc, loaded)
        }
        val resultString = DUMP.dumpToString(merged)
        writeTo.writeText(resultString, StandardCharsets.UTF_8)
    }

    fun deepMergeYaml(dst: Any?, src: Any?): Any? {
        // Политика: src (то, что копируем сейчас) ПЕРЕБИВАЕТ dst при конфликте скаляров.
        if (dst is Map<*, *> && src is Map<*, *>) {
            val out = LinkedHashMap<String, Any?>()
            // сначала старое
            dst.forEach { (k, v) -> if (k != null) out[k.toString()] = v }
            // потом новое (с мержем)
            src.forEach { (k, v) ->
                if (k != null) {
                    val key = k.toString()
                    val existing = out[key]
                    out[key] = deepMergeYaml(existing, v)
                }
            }
            return out
        }
        if (dst is List<*> && src is List<*>) {
            // Самый простой вариант: новая коллекция заменяет старую.
            return src
        }
        return src ?: dst
    }
}
