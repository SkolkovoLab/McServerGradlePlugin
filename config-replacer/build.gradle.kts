plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.3.0"
    id("dev.cherrypizza.mc-server-kit-publish")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
    withSourcesJar()
}

dependencies {
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")
    implementation("org.yaml:snakeyaml:2.5")
}

// Тонкий jar уводим в сторону (classifier=thin), чтобы fat-jar (shadow) занял основное
// имя config-replacer-<version>.jar без коллизии путей вывода.
tasks.jar {
    archiveClassifier.set("thin")
}

tasks.shadowJar {
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "dev.cherrypizza.mcserverkit.configreplacer.ConfigReplacerMain"
    }
}

tasks.named("assemble") { dependsOn(tasks.shadowJar) }

// Публикуем именно fat-jar как основной артефакт — потребитель (.server) просто гоняет
// `java -jar config-replacer.jar`, без транзитивных зависимостей.
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.named("shadowJar"))
            artifact(tasks.named("sourcesJar"))
        }
    }
}
