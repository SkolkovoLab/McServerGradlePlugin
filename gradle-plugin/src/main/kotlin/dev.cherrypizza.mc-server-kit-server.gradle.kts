import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

plugins {
    id("dev.cherrypizza.mc-server-kit-minecraft")
    id("de.eldoria.plugin-yml.paper")
    id("com.gradleup.shadow")
}

// ============================================================
// paper-plugin.yml — НАТИВНОЕ расширение, только дефолты, ничего не закрываем.
// Модуль-потребитель переоткрывает paper {} и переопределяет/добавляет что угодно
// (serverDependencies, permissions, authors, bootstrapper, ...).
// ============================================================
paper {
    name = project.name
    version = rootProject.version.toString()
    main = McServer.DEFAULT_MAIN
    bootstrapper = McServer.DEFAULT_BOOTSTRAPPER
    generateLibrariesJson = false
    apiVersion = "1.21"
}

// ============================================================
// minecraftServer { } — всё, чего нет в plugin-yml. Конфигурируемо, не обязательно.
// ============================================================
val serverExt = extensions.create("minecraftServer", MinecraftServerExtension::class.java)
val mcModule = extensions.getByType(MinecraftModuleExtension::class.java)

// ============================================================
// Bundle-конструктор (consumer-сторона): сервер собирается из слоёв.
// ============================================================
// Саму `bundle`-конфигурацию создаёт `.minecraft` (уровень компонуемого слоя) — здесь
// только используем её для резолва слоёв run_template.
val bundleConfiguration = configurations["bundle"]

// Дефолтный base (`:bootstrap`) — код + paper.main. Добавляем в afterEvaluate, чтобы
// прочитать флаг defaultBootstrap из minecraftServer {}. BOM тулкита (platform) выравнивает
// версии его модулей, поэтому bootstrap тянем без явной версии — её даёт BOM (см. :bom).
afterEvaluate {
    if (serverExt.defaultBootstrap) {
        dependencies.add("api", dependencies.platform("${McServer.GROUP}:mc-server-kit-bom:${McServer.VERSION}"))
        dependencies.add("api", "${McServer.GROUP}:mc-server-kit-bootstrap")
    }
}

// ============================================================
// Конфигурация путей
// ============================================================
val variablesFileName = "template_variables.yml"
val localVariablesFileName = "template_variables.local.yml"

val runDir = projectDir.resolve("run")
val mergedVariablesForBuild = projectDir.resolve("build/libs/$variablesFileName")
val mergedVariablesForRun = runDir.resolve(variablesFileName)
val warmContextDir = projectDir.resolve("build/warm-context")
val imageRuntimeDir = projectDir.resolve("build/image/runtime")

// Синтетический слой со скачанными артефактами (server.jar + plugin-jar'ы по URL+SHA).
val downloadedTemplateDir = layout.buildDirectory.dir("downloaded-template").get().asFile
// Распакованные companion run-template'ы внешних бандлов / базы.
val layersStageDir = layout.buildDirectory.dir("run-template-layers").get().asFile

val useLocalOverride = !project.hasProperty("noLocalOverride")

// configReplacerEnv { env[...] = ... } — per-module env для config-replacer на runServer/warm.
open class RunTaskEnvExtension(val env: MutableMap<String, Any> = mutableMapOf())
val envExtension = extensions.create<RunTaskEnvExtension>("configReplacerEnv")

fun moduleRunTemplateDir(): File = projectDir.resolve(mcModule.runTemplateDirName)
fun persistentRunSubdirs(): List<String> = serverExt.warmCache

// ============================================================
// Резолв слоёв из maven-артефактов (companion run-template zip)
// ============================================================
fun stagedDirFor(notation: String): File = layersStageDir.resolve(notation.replace(":", "_"))

/** Резолвит `group:name:version:run-template@zip` и распаковывает в staging; null если артефакта нет. */
fun resolveAndStageRunTemplate(notation: String): File? {
    val dest = stagedDirFor(notation)
    val dep = dependencies.create(notation).let { it as ExternalModuleDependency }
    dep.artifact {
        name = dep.name
        classifier = "run-template"
        type = "zip"
        extension = "zip"
    }
    val cfg = configurations.detachedConfiguration(dep).apply { isTransitive = false }
    val zip = try {
        cfg.resolve().firstOrNull()
    } catch (e: Exception) {
        logger.warn("[layers] не удалось зарезолвить $notation:run-template@zip — пропускаю слой (${e.message})")
        return null
    } ?: return null
    dest.deleteRecursively()
    dest.mkdirs()
    copy {
        from(zipTree(zip))
        into(dest)
    }
    return dest
}

/** Слои в порядке override-приоритета: base → bundles → downloaded → module. */
fun runTemplateLayers(): List<File> = buildList {
    if (serverExt.useDefaultBase) {
        resolveAndStageRunTemplate("${McServer.GROUP}:mc-server-kit-bootstrap:${McServer.VERSION}")?.let { add(it) }
    }
    bundleConfiguration.dependencies.forEach { dep ->
        when (dep) {
            is ProjectDependency -> {
                val d = project(dep.path).projectDir.resolve("run_template")
                if (d.exists()) add(d)
            }
            is ExternalModuleDependency ->
                resolveAndStageRunTemplate("${dep.group}:${dep.name}:${dep.version}")?.let { add(it) }
        }
    }
    if (downloadedTemplateDir.exists()) add(downloadedTemplateDir)
    val mod = moduleRunTemplateDir()
    if (mod.exists()) add(mod)
}

// base+bundles+downloaded (без module) — для docker-образа.
fun imageLayers(): List<File> {
    val layers = runTemplateLayers()
    val mod = moduleRunTemplateDir()
    return layers.filter { it != mod }
}

// ============================================================
// Merge template_variables
// ============================================================
fun mergeSources(withLocal: Boolean): List<File> = buildList {
    for (layer in runTemplateLayers()) {
        add(layer.resolve(variablesFileName))
        if (withLocal) add(layer.resolve(localVariablesFileName))
    }
}

fun Task.mergeVariablesInto(target: File, withLocal: Boolean) {
    val sources = mergeSources(withLocal)
    val active = sources.filter { it.exists() }
    val suffix = if (withLocal) " (.local подмешан)" else ""
    logger.lifecycle("[$name] ${active.size}/${sources.size} layers active$suffix:")
    active.forEach { logger.lifecycle("  + ${it.relativeTo(rootDir)}") }
    target.parentFile.mkdirs()
    YamlUtils.deepMergeYaml(target, *sources.toTypedArray())
}

// ============================================================
// SHA-256 download
// ============================================================
fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { ins ->
        val buf = ByteArray(8192)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

fun downloadVerified(url: String, sha: String, dest: File) {
    if (dest.exists() && sha256(dest).equals(sha, ignoreCase = true)) {
        logger.lifecycle("[download] up-to-date: ${dest.name}")
        return
    }
    dest.parentFile.mkdirs()
    logger.lifecycle("[download] $url → ${dest.relativeTo(projectDir)}")
    URI(url).toURL().openStream().use { ins -> dest.outputStream().use { out -> ins.copyTo(out) } }
    val actual = sha256(dest)
    if (!actual.equals(sha, ignoreCase = true)) {
        dest.delete()
        throw GradleException("[download] SHA-256 mismatch для $url:\n  expected $sha\n  actual   $actual")
    }
}

// ============================================================
// config-replacer (maven artifact)
// ============================================================
fun resolveConfigReplacerJar(): File {
    val dep = dependencies.create("${McServer.GROUP}:mc-server-kit-config-replacer:${McServer.VERSION}")
    return configurations.detachedConfiguration(dep).apply { isTransitive = false }.resolve().single()
}

// ============================================================
// Tasks
// ============================================================

val downloadServerArtifacts = tasks.register("downloadServerArtifacts") {
    description = "Скачивает и SHA-256-валидирует server.jar / plugin-jar'ы (URL+SHA). Сосуществует с committed."
    group = "build"
    doLast {
        val sj = serverExt.serverJar
        if (!sj.committed && sj.url != null) {
            val sha = sj.sha256 ?: throw GradleException("minecraftServer.serverJar: задан url без sha256")
            downloadVerified(sj.url!!, sha, downloadedTemplateDir.resolve(sj.fileName))
        }
        serverExt.downloadedPlugins.forEach { p ->
            val url = p.url ?: throw GradleException("minecraftServer.plugin { } без url")
            val sha = p.sha256 ?: throw GradleException("minecraftServer.plugin { url=$url } без sha256")
            val name = p.fileName ?: url.substringAfterLast('/')
            downloadVerified(url, sha, downloadedTemplateDir.resolve(p.into).resolve(name))
        }
    }
}

val mergeVariables = tasks.register("mergeVariables") {
    description = "Мёрджит template_variables.yml в build/libs/ для docker-образа (без .local)."
    dependsOn(downloadServerArtifacts)
    doLast {
        mergeVariablesInto(mergedVariablesForBuild, withLocal = false)
        // Гарантируем существование пустых warm-context подпапок — Dockerfile COPY'ит папку целиком.
        persistentRunSubdirs().forEach { warmContextDir.resolve(it).mkdirs() }
    }
}
tasks.named("assemble") { dependsOn(mergeVariables) }

val prepareImageDir = tasks.register<Sync>("prepareImageDir") {
    description = "Собирает base+bundles+downloaded run_template + config-replacer.jar в build/image/runtime для docker."
    dependsOn(downloadServerArtifacts)
    from(provider { imageLayers() })
    // config-replacer.jar кладём прямо в runtime → попадает в /opt/app через один COPY в Dockerfile
    // (entrypoint вызывает `java -jar /opt/app/config-replacer.jar`).
    from(provider { resolveConfigReplacerJar() }) { rename { "config-replacer.jar" } }
    into(imageRuntimeDir)
    exclude(variablesFileName, localVariablesFileName)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
tasks.named("assemble") { dependsOn(prepareImageDir) }

fun cleanRunDir() {
    if (!runDir.exists()) return
    val keepDirs = persistentRunSubdirs().map { runDir.resolve(it).normalize() }
    val keepSet = HashSet<File>()
    for (dir in keepDirs) {
        if (dir.exists()) dir.walkTopDown().forEach { keepSet.add(it) }
        var p: File? = dir.parentFile
        while (p != null && p != runDir && p.startsWith(runDir)) {
            keepSet.add(p); p = p.parentFile
        }
    }
    runDir.walkBottomUp().forEach { file ->
        if (file == runDir) return@forEach
        if (file !in keepSet) file.delete()
    }
}

fun copyTemplatesToRunDir() {
    runTemplateLayers().forEach { it.copyRecursively(runDir, overwrite = true) }
    runDir.resolve(localVariablesFileName).delete()
}

val prepareRunDir = tasks.register("prepareRunDir") {
    description = "Готовит run/ для runServer: шаблоны, merged variables (с .local), config-replacer.jar."
    dependsOn(downloadServerArtifacts)
    doLast {
        cleanRunDir()
        copyTemplatesToRunDir()
        mergeVariablesInto(mergedVariablesForRun, withLocal = useLocalOverride)
        resolveConfigReplacerJar().copyTo(runDir.resolve("config-replacer.jar"), overwrite = true)
    }
}

val runConfigReplacer = tasks.register<JavaExec>("runConfigReplacer") {
    description = "Прогоняет config-replacer: подставляет env-переменные в template_configs."
    dependsOn(prepareRunDir)
    workingDir = runDir
    mainClass.set("-jar")
    args = listOf("config-replacer.jar", "replace", "./template_configs", "./", variablesFileName)
    environment.putAll(envExtension.env)
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Локальный запуск Paper-сервера с этим плагином."
    dependsOn(tasks.named("shadowJar"), prepareRunDir, runConfigReplacer)
    workingDir = runDir
    mainClass.set("-jar")
    doFirst {
        val pluginFile = (tasks.named("shadowJar").get() as org.gradle.jvm.tasks.Jar).archiveFile.get().asFile
        args = listOf(serverExt.serverJar.fileName, "nogui", "-add-plugin=${pluginFile.absolutePath}")
    }
    jvmArgs = listOf("-Xmx4G", "-Dlog4j.configurationFile=log4j2.xml")
}

// ============================================================
// warmServer — прогрев скачиваемых на старте Paper артефактов
// ============================================================
tasks.register("warmServer") {
    description = "Прогревает run/cache|libraries|versions (Paper до Done + graceful stop). Для Dockerfile."
    group = "build"
    dependsOn(prepareRunDir)

    inputs.property("serverJarKey", provider { serverExt.serverJar.let { it.sha256 ?: it.fileName } })
    val pathSensitivity = PathSensitivity.RELATIVE
    runTemplateLayers().forEach { layer ->
        val pluginsDir = layer.resolve("plugins")
        if (pluginsDir.exists()) inputs.dir(pluginsDir).withPathSensitivity(pathSensitivity)
    }
    outputs.dir(warmContextDir)

    doLast {
        val javaExecutable = javaToolchains.launcherFor(java.toolchain).get()
            .executablePath.asFile.absolutePath
        val serverJarName = serverExt.serverJar.fileName

        logger.lifecycle("[warmServer] Перепрогоняю config-replacer с WARM=true...")
        val replacerBuilder = ProcessBuilder(
            javaExecutable, "-jar", "config-replacer.jar",
            "replace", "./template_configs", "./", variablesFileName,
        ).directory(runDir).redirectErrorStream(true)
        replacerBuilder.environment().putAll(envExtension.env.mapValues { it.value.toString() })
        replacerBuilder.environment()["WARM"] = "true"
        val replacerProc = replacerBuilder.start()
        replacerProc.inputStream.bufferedReader().use { r -> r.lineSequence().forEach { logger.lifecycle(it) } }
        if (replacerProc.waitFor() != 0) throw GradleException("[warmServer] config-replacer (warm pass) упал")

        logger.lifecycle("[warmServer] Запускаю Paper для прогрева...")
        val process = ProcessBuilder(
            javaExecutable, "-Xmx2G", "-Xms1G",
            "-Dlog4j.configurationFile=log4j2.xml", "-jar", serverJarName, "nogui",
        ).directory(runDir).redirectErrorStream(true).start()

        val doneRegex = Regex("""Done \(\d+(?:\.\d+)?s\)""")
        val stdin = process.outputStream.bufferedWriter()
        val doneAt = AtomicLong(0L)

        val readerThread = Thread {
            try {
                process.inputStream.bufferedReader().lineSequence().forEach { line ->
                    logger.lifecycle(line)
                    if (doneAt.get() == 0L && doneRegex.containsMatchIn(line)) {
                        doneAt.set(System.currentTimeMillis())
                        logger.lifecycle("[warmServer] Paper готов → жду post-Done downloads")
                    }
                }
            } catch (_: Exception) {
            }
        }.apply { isDaemon = true; start() }

        val overallTimeout = TimeUnit.MINUTES.toMillis(15)
        val postDoneDelay = TimeUnit.SECONDS.toMillis(20)
        val stopGracePeriod = TimeUnit.SECONDS.toMillis(60)
        val startedAt = System.currentTimeMillis()

        var stopSent = false
        while (process.isAlive) {
            val now = System.currentTimeMillis()
            val done = doneAt.get()
            if (done != 0L && now - done >= postDoneDelay && !stopSent) {
                logger.lifecycle("[warmServer] Post-Done delay прошёл — отправляю 'stop'")
                try {
                    stdin.write("stop"); stdin.newLine(); stdin.flush()
                } catch (e: Exception) {
                    logger.warn("[warmServer] stdin.write failed: ${e.message}")
                }
                stopSent = true
            }
            if (now - startedAt > overallTimeout) {
                logger.warn("[warmServer] Overall timeout — force kill")
                process.destroyForcibly(); break
            }
            if (stopSent && now - done > postDoneDelay + stopGracePeriod) {
                logger.warn("[warmServer] Graceful stop не успел — force kill")
                process.destroyForcibly(); break
            }
            Thread.sleep(500)
        }

        process.waitFor(10, TimeUnit.SECONDS)
        readerThread.join(5_000)

        if (doneAt.get() == 0L) {
            throw GradleException(
                "[warmServer] Paper не дошёл до 'Done'. Вероятно плагин завис на коннекте к БД/Redis. " +
                        "Подними инфраструктуру или используй warm-флаг в шаблонах."
            )
        }
        logger.lifecycle("[warmServer] Прогрев завершён. exit=${process.exitValue()}")

        warmContextDir.mkdirs()
        persistentRunSubdirs().forEach { sub ->
            val src = runDir.resolve(sub)
            val dst = warmContextDir.resolve(sub)
            dst.deleteRecursively()
            if (src.exists()) {
                src.copyRecursively(dst, overwrite = true)
                logger.lifecycle("[warmServer] $sub → ${dst.relativeTo(projectDir)}")
            } else dst.mkdirs()
        }
    }
}
