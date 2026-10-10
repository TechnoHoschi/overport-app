package moe.crx.overport.commands

import moe.crx.overport.cli.CliCommand
import moe.crx.overport.patching.PatchStore

object ListPatchesCommand : CliCommand() {
    override fun name() = "patches"
    override fun description() = "Print list of available patches."

    override fun printHelp(): Boolean {
        return false
    }

    override suspend fun execute(args: List<String>): Boolean {
        println("Available patches:")

        PatchStore.all().forEach {
            print("- ")
            print(it.name)
            println(if (it.isRecommended) " (recommended)" else "")
        }

        return true
    }
}