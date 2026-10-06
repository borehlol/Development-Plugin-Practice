package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.random.Random

// /rtp opens a menu for teleporting to a random safe spot in the overworld, nether, or end.
class RtpCommand(private val plugin: JavaPlugin) : CommandExecutor, Listener {

    // size is the width of the square area, centered on 0, 0.
    private enum class Destination(
        val environment: World.Environment,
        val displayName: String,
        val color: TextColor,
        val icon: Material,
        val slot: Int,
        val size: Int,
        val sizeLabel: String,
    ) {
        OVERWORLD(World.Environment.NORMAL, "Overworld", NamedTextColor.GREEN, Material.GRASS_BLOCK, 11, 10_000, "10kx10k"),
        NETHER(World.Environment.NETHER, "Nether", NamedTextColor.RED, Material.NETHERRACK, 13, 5_000, "5kx5k"),
        END(World.Environment.THE_END, "End", NamedTextColor.LIGHT_PURPLE, Material.END_STONE, 15, 5_000, "5kx5k"),
    }

    private class Menu : InventoryHolder {
        lateinit var menu: Inventory
        override fun getInventory() = menu
    }

    private val maxAttempts = 40
    private val searching = mutableSetOf<UUID>()

    private val unsafeGround = setOf(
        Material.LAVA, Material.WATER, Material.MAGMA_BLOCK, Material.CACTUS,
        Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
        Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.BEDROCK,
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED))
            return true
        }
        sender.openInventory(createMenu())
        return true
    }

    private fun createMenu(): Inventory {
        val holder = Menu()
        val inventory = plugin.server.createInventory(holder, 27, Component.text("Random Teleport"))
        holder.menu = inventory

        val filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), emptyList())
        for (slot in 0 until inventory.size) inventory.setItem(slot, filler)

        for (destination in Destination.values()) {
            inventory.setItem(destination.slot, item(
                destination.icon,
                Component.text(destination.displayName, destination.color, TextDecoration.BOLD),
                listOf(
                    Component.text("${destination.sizeLabel} blocks", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to teleport", NamedTextColor.YELLOW),
                ),
            ))
        }
        return inventory
    }

    private fun item(material: Material, name: Component, lore: List<Component>): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(name.decoration(TextDecoration.ITALIC, false))
            meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
        }
        return item
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.topInventory.holder !is Menu) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != event.view.topInventory) return
        val destination = Destination.values().firstOrNull { it.slot == event.slot } ?: return

        player.closeInventory()
        teleport(player, destination)
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is Menu) event.isCancelled = true
    }

    private fun teleport(player: Player, destination: Destination) {
        val world = plugin.server.worlds.firstOrNull { it.environment == destination.environment }
        if (world == null) {
            player.sendMessage(Component.text("The ${destination.displayName} is disabled on this server.").color(NamedTextColor.RED))
            return
        }
        if (!searching.add(player.uniqueId)) {
            player.sendMessage(Component.text("You're already being teleported.").color(NamedTextColor.RED))
            return
        }

        player.sendMessage(Component.text("Searching for a safe location...").color(NamedTextColor.GRAY))
        search(player, world, destination, 1)
    }

    // Loads one random chunk at a time without freezing the server, retrying until a safe spot is found.
    private fun search(player: Player, world: World, destination: Destination, attempt: Int) {
        val half = destination.size / 2
        val x = Random.nextInt(-half, half)
        val z = Random.nextInt(-half, half)

        world.getChunkAtAsync(x shr 4, z shr 4).thenAccept {
            if (!player.isOnline) {
                searching.remove(player.uniqueId)
                return@thenAccept
            }

            val location = findSafeSpot(world, x, z)
            when {
                location != null -> {
                    searching.remove(player.uniqueId)
                    player.teleportAsync(location).thenAccept {
                        player.sendMessage(
                            Component.text("Teleported to ", NamedTextColor.GREEN)
                                .append(Component.text("${location.blockX}, ${location.blockY}, ${location.blockZ}", NamedTextColor.WHITE))
                                .append(Component.text(" in the ", NamedTextColor.GREEN))
                                .append(Component.text(destination.displayName, destination.color))
                        )
                    }
                }
                attempt < maxAttempts -> search(player, world, destination, attempt + 1)
                else -> {
                    searching.remove(player.uniqueId)
                    player.sendMessage(Component.text("Couldn't find a safe location. Try again.").color(NamedTextColor.RED))
                }
            }
        }
    }

    private fun findSafeSpot(world: World, x: Int, z: Int): Location? {
        // The nether has a bedrock roof, so search below it for a floor with headroom.
        val groundY = if (world.environment == World.Environment.NETHER) {
            (32..115).filter { y -> isSafe(world, x, y, z) }.randomOrNull()
        } else {
            world.getHighestBlockYAt(x, z).takeIf { y -> isSafe(world, x, y, z) }
        } ?: return null

        return Location(world, x + 0.5, groundY + 1.0, z + 0.5)
    }

    private fun isSafe(world: World, x: Int, y: Int, z: Int): Boolean {
        val ground = world.getBlockAt(x, y, z)
        val feet = ground.getRelative(0, 1, 0)
        val head = ground.getRelative(0, 2, 0)
        return ground.type.isSolid && ground.type !in unsafeGround &&
            feet.isPassable && !feet.isLiquid &&
            head.isPassable && !head.isLiquid
    }
}
