package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

// /economy <add|take|set> <player> <money|shards> <amount>, plus /economy help for everyone.
class EconomyCommand(private val economy: EconomyManager) : CommandExecutor, TabCompleter {

    private val adminActions = listOf("add", "take", "set")

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val action = args.getOrNull(0)?.lowercase()
        if (action == null || action == "help") {
            sendHelp(sender, label)
            return true
        }

        if (action !in adminActions) {
            sender.sendMessage(Component.text("Unknown subcommand. Try /$label help").color(NamedTextColor.RED))
            return true
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("You don't have permission to do that.").color(NamedTextColor.RED))
            return true
        }

        val currency = args.getOrNull(2)?.let(EconomyManager::parseCurrency)
        val amount = args.getOrNull(3)?.let(EconomyManager::parseAmount)
        // Only set can use 0; adding or taking nothing is a mistake.
        if (args.size != 4 || currency == null || amount == null || (amount == 0.0 && action != "set")) {
            sender.sendMessage(
                Component.text("Usage: /$label $action <player> <money|shards> <amount>").color(NamedTextColor.RED)
            )
            return true
        }

        val target = economy.findByName(args[1])
        if (target == null) {
            sender.sendMessage(Component.text("${args[1]} has never joined this server.").color(NamedTextColor.RED))
            return true
        }

        val (uuid, account) = target
        val current = economy.balance(uuid, currency)
        economy.set(uuid, currency, when (action) {
            "add" -> current + amount
            "take" -> current - amount
            else -> amount
        })

        if (action == "add") {
            val (amountText, color) = when (currency) {
                Currency.MONEY -> "$${EconomyManager.format(amount)}" to NamedTextColor.GREEN
                Currency.SHARDS -> "${EconomyManager.format(amount)}★" to NamedTextColor.LIGHT_PURPLE
            }
            sender.sendMessage(Component.text("Submitting transaction...", NamedTextColor.GRAY))
            sender.sendMessage(Component.text("You added balance to ${account.name}'s account:", NamedTextColor.GREEN))
            sender.sendMessage(
                Component.text("Amount: ", NamedTextColor.GRAY)
                    .append(Component.text(amountText, color))
            )
            return true
        }

        sender.sendMessage(
            Component.text("${account.name} now has ${EconomyManager.format(economy.balance(uuid, currency))} ${currency.name.lowercase()}.")
                .color(NamedTextColor.YELLOW)
        )
        return true
    }

    private fun sendHelp(sender: CommandSender, label: String) {
        sender.sendMessage(
            Component.text("SkinnySMP Economy", NamedTextColor.AQUA, TextDecoration.BOLD)
        )
        helpLine(sender, "/pay <player> <amount>", "Send money to another player")
        helpLine(sender, "/baltop [money|shards] [page]", "See the richest players")

        if (sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("Admin", NamedTextColor.RED, TextDecoration.BOLD))
            helpLine(sender, "/$label add <player> <money|shards> <amount>", "Give a player money or shards")
            helpLine(sender, "/$label take <player> <money|shards> <amount>", "Remove money or shards")
            helpLine(sender, "/$label set <player> <money|shards> <amount>", "Set an exact balance")
        }

        sender.sendMessage(
            Component.text("Amounts can be shortened: ", NamedTextColor.GRAY)
                .append(Component.text("1k, 2.5m, 1b", NamedTextColor.WHITE))
        )
        sender.sendMessage(
            Component.text("Example: ", NamedTextColor.GRAY)
                .append(Component.text(
                    if (sender.hasPermission(ADMIN_PERMISSION)) "/$label add iiSkinny shards 500" else "/pay iiSkinny 1k",
                    NamedTextColor.WHITE,
                ))
        )
    }

    private fun helpLine(sender: CommandSender, usage: String, description: String) {
        sender.sendMessage(
            Component.text(usage, NamedTextColor.YELLOW)
                .append(Component.text(" - $description", NamedTextColor.GRAY))
        )
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val isAdmin = sender.hasPermission(ADMIN_PERMISSION)
        val options = when (args.size) {
            1 -> if (isAdmin) listOf("help") + adminActions else listOf("help")
            2 -> if (isAdmin && args[0].lowercase() in adminActions) sender.server.onlinePlayers.map { it.name } else emptyList()
            3 -> if (isAdmin && args[0].lowercase() in adminActions) listOf("money", "shards") else emptyList()
            else -> emptyList()
        }
        return options.filter { it.lowercase().startsWith(args.last().lowercase()) }
    }

    companion object {
        const val ADMIN_PERMISSION = "skinny.eco"
    }
}
