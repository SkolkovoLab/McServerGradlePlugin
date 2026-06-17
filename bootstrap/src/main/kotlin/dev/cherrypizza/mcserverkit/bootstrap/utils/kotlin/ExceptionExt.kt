package dev.cherrypizza.mcserverkit.bootstrap.utils.kotlin

import dev.cherrypizza.mcserverkit.bootstrap.utils.kyori.miniMessage

fun Exception.formatToPlayer() =
    "<gray>An error occurred (<aqua>${javaClass.simpleName}</aqua>)</gray>: <yellow>$message".miniMessage()
