package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class SpeedCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED))
            return true
        }

        val level = args.getOrNull(0)?.toIntOrNull()
        if (level == null || level !in 1..10) {
            sender.sendMessage(Component.text("Usage: /speed <1-10>").color(NamedTextColor.RED))
            return true
        }

        if (sender.isFlying) {
            sender.flySpeed = level / 10f
            sender.sendMessage(Component.text("Your fly speed has been set to $level.").color(NamedTextColor.YELLOW))
        } else {
            sender.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 999999, level - 1, false, false))
            sender.sendMessage(Component.text("Your speed has been set to $level.").color(NamedTextColor.YELLOW))
        }

        return true
    }
}
