package moe.crx.overport.cli

import moe.crx.overport.commands.AnalyzeApplicationCommand
import moe.crx.overport.commands.DisplayHelpCommand
import moe.crx.overport.commands.ListPatchesCommand
import moe.crx.overport.commands.PatchApplicationCommand
import moe.crx.overport.commands.VersionManagerCommand

object CommandStore {
    val COMMANDS = listOf(
        ListPatchesCommand,
        PatchApplicationCommand,
        AnalyzeApplicationCommand,
        DisplayHelpCommand,
        VersionManagerCommand,
    ).associateBy { it.name() }
}