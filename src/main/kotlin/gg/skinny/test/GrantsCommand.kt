package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

// /grants <player> opens a menu of every rank that can be given to that player.
// /grants <player> <rank> sets it directly, which also works from the console.
class GrantsCommand(
    private val plugin: JavaPlugin,
    private val ranks: RankManager,
    private val economy: EconomyManager,
) : CommandExecutor, TabCompleter, Listener {

    private class Menu(val target: UUID, val targetName: String) : InventoryHolder {
        lateinit var menu: Inventory
        override fun getInventory() = menu
    }

    // The ranks fill the third row, one per slot, highest on the left.
    private val firstRankSlot = 18

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty() || args.size > 2) {
            sender.sendMessage(Component.text("Usage: /$label <player> [rank]").color(NamedTextColor.RED))
            return true
        }

        // Players who haven't joined since ranks moved to the database are only known to the economy.
        val target = ranks.findByName(args[0])
            ?: economy.findByName(args[0])?.let { (uuid, account) -> uuid to account.name }
        if (target == null) {
            sender.sendMessage(Component.text("${args[0]} has never joined the network.").color(NamedTextColor.RED))
            return true
        }
        val (uuid, name) = target

        if (args.size == 2) {
            val rank = parseRank(args[1])
            if (rank == null) {
                sender.sendMessage(
                    Component.text("Unknown rank. Ranks: ${rankKeys().joinToString(", ")}").color(NamedTextColor.RED)
                )
                return true
            }
            grant(sender, uuid, name, rank)
            return true
        }

        if (sender !is Player) {
            sender.sendMessage(Component.text("Usage: /$label <player> <rank>").color(NamedTextColor.RED))
            return true
        }
        sender.openInventory(createMenu(sender, uuid, name))
        return true
    }

    private fun createMenu(viewer: Player, target: UUID, targetName: String): Inventory {
        val holder = Menu(target, targetName)
        val inventory = plugin.server.createInventory(holder, 36, Component.text("Grants: $targetName"))
        holder.menu = inventory

        val filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), emptyList())
        for (slot in 0 until inventory.size) inventory.setItem(slot, filler)

        val current = ranks.rank(target)
        val head = item(
            Material.PLAYER_HEAD,
            current.format(targetName),
            listOf(
                Component.text("Current rank: ", NamedTextColor.GRAY)
                    .append(Component.text(current.displayName, current.color)),
            ),
        )
        head.editMeta(SkullMeta::class.java) { it.owningPlayer = plugin.server.getOfflinePlayer(target) }
        inventory.setItem(4, head)

        Rank.values().forEachIndexed { i, rank ->
            val lore = mutableListOf(
                Component.text("Preview: ", NamedTextColor.GRAY).append(rank.format(targetName)),
                Component.empty(),
            )
            if (rank.perks.isNotEmpty()) {
                lore += Component.text("Unlocks:", NamedTextColor.GRAY)
                rank.perks.forEach { lore += Component.text(" ${it.label}", NamedTextColor.WHITE) }
            }
            val below = rank.below()
            if (below != null && below != Rank.MEMBER) {
                lore += Component.text("+ everything from ", NamedTextColor.GRAY)
                    .append(Component.text(below.displayName, below.color))
            }
            if (rank != Rank.MEMBER) lore += Component.empty()

            lore += when {
                rank == current -> Component.text("Current rank", NamedTextColor.GREEN)
                denyReason(viewer, target, rank) != null -> Component.text("You can't grant this rank", NamedTextColor.RED)
                else -> Component.text("Click to grant", NamedTextColor.YELLOW)
            }

            val icon = item(rank.icon, Component.text(rank.displayName, rank.color, TextDecoration.BOLD), lore)
            if (rank == current) {
                icon.editMeta {
                    it.addEnchant(Enchantment.LUCK, 1, true)
                    it.addItemFlags(ItemFlag.HIDE_ENCHANTS)
                }
            }
            inventory.setItem(firstRankSlot + i, icon)
        }
        return inventory
    }

    private fun item(material: Material, name: Component, lore: List<Component>): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.displayName(name.decoration(TextDecoration.ITALIC, false))
            meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)
        }
        return item
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val menu = event.view.topInventory.holder as? Menu ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != event.view.topInventory) return
        val rank = Rank.values().getOrNull(event.slot - firstRankSlot) ?: return
        if (event.slot < firstRankSlot || rank == ranks.rank(menu.target)) return

        grant(player, menu.target, menu.targetName, rank)
        player.openInventory(createMenu(player, menu.target, menu.targetName))
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is Menu) event.isCancelled = true
    }

    // Ops and the console can grant anything. Staff can only grant ranks below their own,
    // and only to players below them, so nobody can promote themselves or demote a superior.
    private fun denyReason(sender: CommandSender, target: UUID, rank: Rank): String? {
        if (sender !is Player || sender.isOp) return null
        val own = ranks.rank(sender.uniqueId)
        return when {
            !own.isAbove(rank) -> "You can only grant ranks below your own."
            target != sender.uniqueId && !own.isAbove(ranks.rank(target)) ->
                "You can't change the rank of someone at or above your rank."
            else -> null
        }
    }

    private fun grant(sender: CommandSender, target: UUID, targetName: String, rank: Rank) {
        val reason = denyReason(sender, target, rank)
        if (reason != null) {
            sender.sendMessage(Component.text(reason).color(NamedTextColor.RED))
            return
        }
        ranks.setRank(target, targetName, rank).exceptionally {
            if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable {
                sender.sendMessage(
                    Component.text("Couldn't save $targetName's rank, so it will reset on restart. Check the server log.")
                        .color(NamedTextColor.RED)
                )
            })
            null
        }
        sender.sendMessage(
            Component.text("Set $targetName's rank to ", NamedTextColor.GREEN)
                .append(Component.text(rank.displayName, rank.color, TextDecoration.BOLD))
        )

        val targetPlayer = plugin.server.getPlayer(target)
        if (targetPlayer != null && targetPlayer != sender) {
            targetPlayer.sendMessage(
                Component.text("Your rank is now ", NamedTextColor.GREEN)
                    .append(Component.text(rank.displayName, rank.color, TextDecoration.BOLD))
            )
        }
    }

    // "srmod", "sr_mod", "skinnyplus" and "skinny+" all work.
    private fun parseRank(input: String): Rank? {
        val key = input.lowercase().replace("+", "plus").filter { it.isLetterOrDigit() }
        return Rank.values().firstOrNull { rankKey(it) == key }
    }

    private fun rankKey(rank: Rank) = rank.name.lowercase().replace("_", "")

    private fun rankKeys() = Rank.values().map(::rankKey)

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val options = when (args.size) {
            1 -> sender.server.onlinePlayers.map { it.name }
            2 -> rankKeys()
            else -> emptyList()
        }
        return options.filter { it.lowercase().startsWith(args.last().lowercase()) }
    }
}
