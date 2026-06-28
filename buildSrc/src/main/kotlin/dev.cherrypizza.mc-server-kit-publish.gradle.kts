plugins {
    `maven-publish`
}

group = "dev.cherrypizza"
version = "1.0.3-SNAPSHOT"

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (!name.endsWith("PluginMarkerMaven")) {
            artifactId = "mc-server-kit-$artifactId"
        }
    }

    repositories {
        maven("https://repo.cherry.pizza/repository/maven-public-hosted") {
            name = "cherry"
            credentials {
                username = providers.gradleProperty("CHERRY_USERNAME").orNull
                password = providers.gradleProperty("CHERRY_PASSWORD").orNull
            }
        }
    }
}