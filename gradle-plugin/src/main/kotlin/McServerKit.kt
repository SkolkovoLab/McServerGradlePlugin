import McServerKit.VERSION
import McServerKit.bootstrapDependency
import org.gradle.api.Action
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler

/**
 * Координаты и дефолты тулкита. [VERSION] = `project.version` gradle-plugin'а
 * (генерится в build, см. [MC_SERVER_VERSION]). `:config-replacer` тулкит подтягивает сам;
 * `:bootstrap` потребитель подключает вручную через [bootstrapDependency] (opt-in).
 */
object McServerKit {
    const val GROUP = "dev.cherrypizza"
    const val VERSION = MC_SERVER_VERSION
    const val PLATFORM_DEPENDENCY = "$GROUP:mc-server-kit-bom:$VERSION"

    /**
     * Координата bootstrap-модуля для РУЧНОГО (opt-in) подключения в build.gradle.kts потребителя:
     * ```
     * dependencies { api(McServerKit.bootstrapDependency()) }
     * ```
     * Автоматически bootstrap больше не подключается. Версия зашита (= версия тулкита), BOM не нужен.
     */
    fun DependencyHandler.bootstrapDependency(): Dependency? = add("api", "$GROUP:mc-server-kit-bootstrap:$VERSION")

    const val DEFAULT_PAPER_VERSION = "1.21.11-R0.1-SNAPSHOT"
    const val DEFAULT_SERVER_JAR = "server.jar"

    const val DEFAULT_MAIN = "dev.cherrypizza.mcserverkit.bootstrap.MicronautServerPlugin"
    const val DEFAULT_BOOTSTRAPPER = "dev.cherrypizza.mcserverkit.bootstrap.MicronautServerBootstrap"

    /**
     * Дефолтный whitelist прогреваемых директорий (то, что Paper качает на старте
     * вне зависимости от плагинов). Плагин-специфику (CMI/LuckPerms/...) потребитель
     * добавляет через `minecraftServer { warmCache += ... }`.
     */
    val DEFAULT_WARM_CACHE: List<String> = listOf(
        "cache",                    // Mojang remap, Paper meta
        "libraries",                // MavenLibraryResolver
        "versions",                 // распакованный server.jar
        "plugins/.paper-remapped",  // реобфусцированные Mojang→Spigot jar'ы
    )
}

/** Откуда брать server.jar: скачать по URL+SHA либо взять уже лежащий в run_template. */
open class ServerJarSpec {
    var url: String? = null
    var sha256: String? = null

    /** Имя файла server-jar'а в корне run_template (его же читает entrypoint/CMD потребителя). */
    var fileName: String = McServerKit.DEFAULT_SERVER_JAR

    /** true → джар уже закоммичен в run_template, скачивать не нужно. */
    var committed: Boolean = false

    /** Взять закоммиченный джар [name] из run_template (без скачивания). */
    fun committed(name: String) {
        committed = true
        fileName = name
    }

    /** Скачать server-jar по [url], проверить SHA-256 [sha256], положить как [fileName]. */
    @JvmOverloads
    fun download(url: String, sha256: String, fileName: String = this.fileName) {
        this.url = url
        this.sha256 = sha256
        this.fileName = fileName
        this.committed = false
    }
}

/** Скачиваемый по URL+SHA plugin-jar (сосуществует с закоммиченными в run_template/plugins). */
open class PluginDownloadSpec {
    var url: String? = null
    var sha256: String? = null

    /** Подпапка внутри run_template, куда класть. По умолчанию `plugins`. */
    var into: String = "plugins"

    /** Имя файла; по умолчанию выводится из URL. */
    var fileName: String? = null
}

/**
 * `.minecraft`-уровень: владелец понятия `run_template`. Применяется и к library-модулям
 * (бандлам), и к серверам.
 *
 * Версия Paper (paperweight dev-bundle) задаётся НЕ здесь, а gradle-property
 * `mcserverkit.paperVersion` (репо-wide; дефолт [McServerKit.DEFAULT_PAPER_VERSION]) —
 * читать её на этапе объявления зависимости в extension'е нельзя из-за порядка
 * afterEvaluate относительно самого paperweight.
 */
open class MinecraftModuleExtension {
    /** Имя директории слоя run_template внутри модуля (относительно projectDir). */
    var runTemplateDirName: String = "run_template"
}

/**
 * `.server`-уровень: всё, чего нет в plugin-yml. Конфигурируемо, ничего не обязательно.
 */
open class MinecraftServerExtension {
    val serverJar = ServerJarSpec()
    val downloadedPlugins = mutableListOf<PluginDownloadSpec>()

    /** Прогреваемые директории (см. [McServerKit.DEFAULT_WARM_CACHE]). Добавляй плагин-специфику. */
    var warmCache: List<String> = McServerKit.DEFAULT_WARM_CACHE

    fun serverJar(action: Action<ServerJarSpec>) = action.execute(serverJar)

    fun plugin(action: Action<PluginDownloadSpec>) {
        val spec = PluginDownloadSpec()
        action.execute(spec)
        downloadedPlugins.add(spec)
    }
}
