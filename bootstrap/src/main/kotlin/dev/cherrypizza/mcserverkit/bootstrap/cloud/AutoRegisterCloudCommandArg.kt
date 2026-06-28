package dev.cherrypizza.mcserverkit.bootstrap.cloud

import org.bukkit.command.CommandSender
import org.incendo.cloud.parser.ArgumentParser
import org.incendo.cloud.suggestion.SuggestionProvider

interface AutoRegisterCloudCommandArg<T> : ArgumentParser.FutureArgumentParser<CommandSender, T>,
    SuggestionProvider<CommandSender> {

    val name: String?
        get() = null
    val type: Class<T>
}