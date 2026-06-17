plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "dev.cherrypizza.mcserver"
version = "0.1.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/")
}
java {
    withSourcesJar()
}
dependencies {
    // Делает version-catalog accessor (LibrariesForLibs) доступным внутри
    // precompiled-script-плагинов (.base/.minecraft используют `the<LibrariesForLibs>()`).
    compileOnly(files(libs::class.java.protectionDomain.codeSource.location))

    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("io.micronaut.gradle:micronaut-minimal-plugin:${libs.versions.micronaut.plugin.get()}")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:${libs.versions.ksp.get()}")
    implementation("de.eldoria.plugin-yml.paper:de.eldoria.plugin-yml.paper.gradle.plugin:${libs.versions.plugin.yml.get()}")
    implementation("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:${libs.versions.shadow.get()}")
    // paperweight НЕ тянем: платформа (paper-api / paperweight) — уровень потребителя.

    // YamlUtils — мерж слоёв template_variables.yml
    implementation("org.snakeyaml:snakeyaml-engine:${libs.versions.snakeyaml.engine.get()}")
}

publishing {
    repositories {
        maven {
            name = "toolkit"
            url = uri(
                file(
                    providers.gradleProperty("mcserverPublishRepo")
                        .getOrElse("/mnt/data/Database/GradlePluginRepository")
                )
            )
        }
    }
}
