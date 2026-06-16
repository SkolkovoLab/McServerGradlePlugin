import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("dev.cherrypizza.mcserver.java")
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(25)
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
}
