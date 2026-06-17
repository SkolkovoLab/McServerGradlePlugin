plugins {
    `kotlin-dsl`
}


// buildSrc держит ТОЛЬКО внутренние конвенции сборки (например, .publish), которыми
// раньше занимались allprojects/subprojects. Конвенции самого тулкита (.base/.minecraft/
// .server и т.п.) живут в :gradle-plugin и публикуются наружу — здесь их нет.
repositories {
    gradlePluginPortal()
    mavenCentral()
}
