package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID

// Players can only break or place blocks while build mode is on. /build toggles it.
class BuildCommand : CommandExecutor, Listener {

    private val building = mutableSetOf<UUID>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED))
            return true
        }

        if (building.remove(sender.uniqueId)) {
            sender.sendMessage(Component.text("Build mode disabled.").color(NamedTextColor.RED))
        } else {
            building.add(sender.uniqueId)
            sender.sendMessage(Component.text("Build mode enabled.").color(NamedTextColor.GREEN))
        }
        return true
    }

    @EventHandler(ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) = block(event.player, event)

    @EventHandler(ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) = block(event.player, event)

    // Buckets place and remove water/lava, so they count as building too.
    @EventHandler(ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) = block(event.player, event)

    @EventHandler(ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) = block(event.player, event)

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        building.remove(event.player.uniqueId)
    }

    private fun block(player: Player, event: Cancellable) {
        if (player.uniqueId in building) return
        event.isCancelled = true

        val hint = if (player.hasPermission("skinny.build")) "Type /build to break or place blocks." else "You can't build here."
        player.sendActionBar(Component.text(hint).color(NamedTextColor.RED))
    }
}
