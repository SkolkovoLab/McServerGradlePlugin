plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.micronaut.minimal.library)
    id("dev.cherrypizza.mc-server-kit-publish")
}

// bootstrap развязан от конвенций тулкита (.minecraft): он не применяется как minecraft-модуль
// и не несёт run_template. Это обычная Kotlin/Micronaut-библиотека, поэтому стек, который
// раньше приходил из .base/.kotlin/.java, задаётся здесь напрямую.
java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
    withSourcesJar()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

dependencies {
    // Стек, ранее приходивший из .base (family-BOM'ы + micronaut + serde/ksp).
    api(platform(libs.kotlin.bom))
    api(platform(libs.kotlinx.coroutines.bom))
    api(platform(libs.micronaut.bom))
    api(libs.micronaut.kotlin.runtime)
    api(libs.micronaut.serde.jackson)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    ksp(libs.micronaut.serde.processor)

    // bootstrap трогает только публичный Paper API (Bukkit/adventure/paper-bootstrap),
    // без NMS — поэтому компилируемся против paper-api (compileOnly), БЕЗ paperweight.
    // Потребитель, которому нужен NMS, применяет paperweight на своём уровне (build-logic).
    compileOnly("io.papermc.paper:paper-api:${libs.versions.paper.get()}")

    api(libs.kotlinx.coroutines.core)
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Cloud — generic command framework (CloudCommand/CloudCommandInitializer)
    api(libs.cloud.core)
    api(libs.cloud.annotations)
    api(libs.cloud.kotlin.coroutines)
    api(libs.cloud.kotlin.coroutines.annotations)
    api(libs.cloud.kotlin.extensions)
    api(libs.cloud.paper)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
