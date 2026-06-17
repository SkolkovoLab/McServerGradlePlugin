plugins {
    `java-platform`
    id("dev.cherrypizza.mc-server-kit-publish")
}

// Maven BOM тулкита: фиксирует версии собственных модулей в одной точке. Потребитель
// импортирует `platform("dev.cherrypizza:mc-server-kit-bom:<version>")` и получает согласованные
// версии артефактов тулкита. Внешние библиотеки (kotlin/micronaut/cloud/...) остаются в
// gradle/libs.versions.toml — BOM их не дублирует.
//
// gradle-plugin в BOM НЕ включён: он применяется как Gradle-плагин (pluginManagement), а не
// подтягивается как обычная зависимость.
dependencies {
    constraints {
        api(project(":config-replacer"))
        api(project(":bootstrap"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
        }
    }
}
