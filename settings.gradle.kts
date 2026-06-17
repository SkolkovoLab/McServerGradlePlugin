rootProject.name = "mc-server-kit"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
    // Каталог `libs` Gradle создаёт автоматически из gradle/libs.versions.toml.
}
// gradle-plugin теперь обычный подпроект (а не includeBuild): он только компилирует и
// публикует конвенции тулкита наружу; внутри сборки их никто не применяет (bootstrap
// развязан). Внутренние конвенции (.publish) живут в buildSrc.
include(
    "bom",
    "gradle-plugin",
    "config-replacer",
    "bootstrap",
)
