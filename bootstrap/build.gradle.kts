plugins {
    id("dev.cherrypizza.mcserver.minecraft")
    `maven-publish`
}

dependencies {
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

// Публикуем код-jar. Companion-артефакт run_template (classifier=run-template) прицепляет
// convention `.minecraft` автоматически (см. dev.cherrypizza.mcserver.minecraft.gradle.kts).
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
