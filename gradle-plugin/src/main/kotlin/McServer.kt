import McServer.VERSION
import org.gradle.api.Action

/**
 * Координаты и дефолты тулкита. [VERSION] = `project.version` gradle-plugin'а
 * (генерится в build из конвенции `.publish`, см. [MC_SERVER_VERSION]); `.server`
 * использует её, чтобы авто-подтянуть `:bootstrap` (база run_template + дефолтный
 * main-класс) и `:config-replacer`.
 */
object McServer {
    const val GROUP = "dev.cherrypizza"
    const val VERSION = MC_SERVER_VERSION
    const val PLATFORM_DEPENDENCY = "$GROUP:mc-server-kit-bom:$VERSION"

    const val DEFAULT_PAPER_VERSION = "1.21.11-R0.1-SNAPSHOT"
    const val DEFAULT_SERVER_JAR = "server.jar"

    const val DEFAULT_MAIN = "dev.cherrypizza.mcserver.bootstrap.MicronautServerPlugin"
    const val DEFAULT_BOOTSTRAPPER = "dev.cherrypizza.mcserver.bootstrap.MicronautServerBootstrap"

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
    var fileName: String = McServer.DEFAULT_SERVER_JAR

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
 * `mcserver.paperVersion` (репо-wide; дефолт [McServer.DEFAULT_PAPER_VERSION]) —
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

    /** Прогреваемые директории (см. [McServer.DEFAULT_WARM_CACHE]). Добавляй плагин-специфику. */
    var warmCache: List<String> = McServer.DEFAULT_WARM_CACHE

    /** Подмешивать ли базовый слой run_template из `:bootstrap` (companion-артефакт). */
    var useDefaultBase: Boolean = true

    /** Добавлять ли `api(:bootstrap)` + дефолтный `paper.main`. Выключи, если свой main-класс. */
    var defaultBootstrap: Boolean = true

    fun serverJar(action: Action<ServerJarSpec>) = action.execute(serverJar)

    fun plugin(action: Action<PluginDownloadSpec>) {
        val spec = PluginDownloadSpec()
        action.execute(spec)
        downloadedPlugins.add(spec)
    }
}
