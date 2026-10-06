package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

// /spawn teleports to the overworld spawn after a 5 second countdown. Moving cancels it.
class SpawnCommand(private val plugin: JavaPlugin) : CommandExecutor, Listener {

    private val countdownSeconds = 5
    private val pending = mutableMapOf<UUID, BukkitTask>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED))
            return true
        }
        if (sender.uniqueId in pending) {
            sender.sendMessage(Component.text("You're already teleporting to spawn.").color(NamedTextColor.RED))
            return true
        }

        sender.sendMessage(
            Component.text("Teleporting to spawn in $countdownSeconds seconds. Don't move!").color(NamedTextColor.YELLOW)
        )

        var secondsLeft = countdownSeconds
        pending[sender.uniqueId] = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (secondsLeft > 0) {
                sender.sendActionBar(
                    Component.text("Teleporting in ", NamedTextColor.YELLOW)
                        .append(Component.text("${secondsLeft}s", NamedTextColor.WHITE))
                )
                sender.playSound(sender.location, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f)
                secondsLeft--
                return@Runnable
            }

            pending.remove(sender.uniqueId)?.cancel()
            sender.teleportAsync(spawnLocation()).thenAccept {
                sender.sendMessage(Component.text("Teleported to spawn.").color(NamedTextColor.YELLOW))
                sender.playSound(sender.location, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f)
            }
        }, 0L, 20L)
        return true
    }

    // The spot saved with /setspawn (including facing direction), or the overworld spawn if none is set.
    // Always the overworld, even when used from the nether or end.
    private fun spawnLocation(): Location =
        plugin.config.getLocation("spawn")
            ?: plugin.server.worlds.first().spawnLocation.toCenterLocation().apply { y = blockY.toDouble() }

    @EventHandler(ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        // Looking around is fine; only walking to a different block cancels.
        if (event.from.blockX == event.to.blockX && event.from.blockY == event.to.blockY && event.from.blockZ == event.to.blockZ) return

        val task = pending.remove(event.player.uniqueId) ?: return
        task.cancel()
        event.player.sendActionBar(Component.empty())
        event.player.sendMessage(Component.text("Teleport cancelled because you moved.").color(NamedTextColor.RED))
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pending.remove(event.player.uniqueId)?.cancel()
    }
}
