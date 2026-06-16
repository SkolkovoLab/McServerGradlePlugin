// Общая координата/версия тулкита и единая точка публикации для подпроектов
// (config-replacer, bootstrap). gradle-plugin — отдельный includeBuild, у него
// своя такая же настройка публикации (см. gradle-plugin/build.gradle.kts).
allprojects {
    group = "dev.cherrypizza.mcserver"
    version = "0.1.0"
}

val publishRepoPath = providers.gradleProperty("mcserverPublishRepo")
    .getOrElse("/mnt/data/Database/GradlePluginRepository")

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "toolkit"
                    url = uri(file(publishRepoPath))
                }
            }
        }
    }
}
