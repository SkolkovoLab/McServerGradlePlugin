plugins {
    `maven-publish`
}

group = "dev.cherrypizza"
version = "1.0.0"

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (!name.endsWith("PluginMarkerMaven")) {
            artifactId = "mc-server-kit-$artifactId"
        }
    }

    repositories {
        maven {
            url = uri(
                file(
                    providers.gradleProperty("mcserverPublishRepo")
                        .getOrElse("/mnt/data/Database/GradlePluginRepository")
                )
            )
        }
    }
}
