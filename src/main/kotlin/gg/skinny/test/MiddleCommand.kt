package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

// Centers the player on the block they're standing on, keeping their height and facing direction.
class MiddleCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED))
            return true
        }

        val location = sender.location
        location.x = location.blockX + 0.5
        location.z = location.blockZ + 0.5
        sender.teleport(location)
        sender.sendMessage(Component.text("Moved to the middle of the block.").color(NamedTextColor.YELLOW))
        return true
    }
}
