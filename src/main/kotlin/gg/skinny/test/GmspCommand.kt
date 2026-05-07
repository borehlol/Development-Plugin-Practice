package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class GmspCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players may use this command.").color(NamedTextColor.BLUE))
            return true
        }

        sender.gameMode = GameMode.SPECTATOR
        sender.sendMessage(Component.text("Your game mode has been set to spectator ", NamedTextColor.BLUE))
        return true
    }
}