plugins {
    id("dev.cherrypizza.mc-server-kit-kotlin")
    id("io.micronaut.minimal.library")
    id("com.google.devtools.ksp")
}

dependencies {
    // Координаты зашиты в плагин (McServerDeps), а НЕ берутся из version-catalog
    // потребителя: это ПУБЛИКУЕМАЯ конвенция, она должна работать на любом чужом
    // libs.versions.toml. Раньше тут был `the<LibrariesForLibs>()` — он биндился на
    // каталог потребителя и ломал сборку. Версии живут в gradle/libs.versions.toml
    // тулкита и вшиваются в McServerDeps на этапе сборки :gradle-plugin.

    // Family-BOM'ы ПЕРЕД micronaut.bom — выравнивают всё семейство kotlin/coroutines
    // на одну версию на всех classpath'ах (см. cr-base в исходном проекте).
    api(platform(McServerDeps.KOTLIN_BOM))
    api(platform(McServerDeps.KOTLINX_COROUTINES_BOM))

    api(platform(McServerDeps.MICRONAUT_BOM))
    api(McServerDeps.MICRONAUT_KOTLIN_RUNTIME)
    api(McServerDeps.MICRONAUT_SERDE_JACKSON)
    implementation(McServerDeps.JACKSON_DATATYPE_JSR310)

    ksp(McServerDeps.MICRONAUT_SERDE_PROCESSOR)
}
