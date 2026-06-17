package dev.cherrypizza.mcserverkit.bootstrap.utils.kyori

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound

fun Key.sound(
    source: Sound.Source = Sound.Source.MASTER,
    volume: Float = 1f,
    pitch: Float = 1f,
) = Sound.sound(this, source, volume, pitch)

fun Sound.toBuilder(): Sound.Builder = Sound.sound(this)

fun Sound.modify(operator: Sound.Builder.() -> Unit): Sound =
    toBuilder().apply(operator).build()

fun Sound.withKey(key: Key): Sound =
    modify { type(key) }
