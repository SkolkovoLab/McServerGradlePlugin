import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("dev.cherrypizza.mc-server-kit-kotlin")
    id("io.micronaut.minimal.library")
    id("com.google.devtools.ksp")
}

val libs = the<LibrariesForLibs>()

dependencies {
    // Family-BOM'ы ПЕРЕД micronaut.bom — выравнивают всё семейство kotlin/coroutines
    // на одну версию на всех classpath'ах (см. cr-base в исходном проекте).
    api(platform(libs.kotlin.bom))
    api(platform(libs.kotlinx.coroutines.bom))

    api(platform(libs.micronaut.bom))
    api(libs.micronaut.kotlin.runtime)
    api(libs.micronaut.serde.jackson)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    ksp(libs.micronaut.serde.processor)
}
