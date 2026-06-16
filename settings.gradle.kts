pluginManagement {
    // Конвенции тулкита подключаются как includeBuild — так его собственные модули
    // (bootstrap) догфудят опубликованные плагины, а `:gradle-plugin:publish` отдаёт
    // их наружу. Один источник, без дублирования.
    includeBuild("gradle-plugin")
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

rootProject.name = "mc-server-kit"

include(
    "config-replacer",
    "bootstrap",
)
