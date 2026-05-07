package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class GmcCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED))
            return true
        }

        sender.gameMode = GameMode.CREATIVE
        sender.sendMessage(Component.text("Your gamemode has been set to creative.").color(NamedTextColor.YELLOW))
        return true
    }
}
