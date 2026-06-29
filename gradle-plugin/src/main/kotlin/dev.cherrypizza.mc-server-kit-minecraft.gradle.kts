plugins {
    `java-library`
}

// `.minecraft`-уровневая конфигурация (владелец понятия run_template).
//
// ВАЖНО: paperweight здесь НЕ применяется — это уровень конкретного проекта-потребителя
// (paperweight-userdev имеет classloader-баг при применении к нескольким модулям из
// опубликованного binary-плагина; корректно — через buildSrc/build-logic потребителя).
// Потребитель сам выбирает платформу: paper-api (compileOnly) для plain-плагинов либо
// paperweight в своей build-logic-конвенции для NMS. См. README тулкита.
val mcModule = extensions.create("minecraft", MinecraftModuleExtension::class.java)

// ============================================================
// bundle — компонуемые слои на уровне minecraft-модуля
// ============================================================
// Любой minecraft-модуль (бандл или сервер) может объявлять bundle(...): зависимость идёт
// и в `api` (код подмешивается транзитивно), а consumer-конвенция `.server` использует её
// для сборки слоёв run_template. Создаётся здесь, чтобы понятие слоя жило на уровне
// `.minecraft`, а не только у сервера.
val bundleConfiguration = configurations.create("bundle")
configurations.named("api") { extendsFrom(bundleConfiguration) }

// ============================================================
// run_template producer
// ============================================================
// Если у модуля есть run_template/, пакуем его в companion-артефакт `run-template`(zip).
// Так слой конфигов едет вместе с jar'ом при публикации — потребитель (.server) резолвит
// его как `group:name:version:run-template@zip` и распаковывает в свой staging.
val packageRunTemplate = tasks.register<Zip>("packageRunTemplate") {
    description = "Пакует run_template модуля в companion-артефакт (classifier=run-template)."
    group = "publishing"
    archiveClassifier.set("run-template")
    archiveExtension.set("zip")
    destinationDirectory.set(layout.buildDirectory.dir("run-template"))
    includeEmptyDirs = true
    from(provider { layout.projectDirectory.dir(mcModule.runTemplateDirName) })
    onlyIf { layout.projectDirectory.dir(mcModule.runTemplateDirName).asFile.exists() }
}

// При наличии run_template — прицепляем zip как companion-артефакт к maven-публикации модуля.
plugins.withId("maven-publish") {
    if (layout.projectDirectory.dir(mcModule.runTemplateDirName).asFile.exists()) {
        extensions.configure<PublishingExtension> {
            publications.withType(MavenPublication::class.java).configureEach {
                artifact(packageRunTemplate) {
                    classifier = "run-template"
                    extension = "zip"
                }
            }
        }
    }
}
