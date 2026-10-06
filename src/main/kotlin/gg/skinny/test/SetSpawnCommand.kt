package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

// Saves the exact spot and facing direction for /spawn, and moves the world spawn there too.
class SetSpawnCommand(private val plugin: JavaPlugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED))
            return true
        }

        val location = sender.location
        plugin.config.set("spawn", location)
        plugin.saveConfig()
        sender.world.setSpawnLocation(location)

        sender.sendMessage(
            Component.text("Spawn set to ${location.blockX}, ${location.blockY}, ${location.blockZ}, facing ${facing(location.yaw)}.")
                .color(NamedTextColor.YELLOW)
        )
        return true
    }

    private fun facing(yaw: Float): String {
        val directions = listOf("south", "southwest", "west", "northwest", "north", "northeast", "east", "southeast")
        val normalized = ((yaw % 360) + 360) % 360
        return directions[Math.round(normalized / 45f) % 8]
    }
}
