package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class BaltopCommand(private val economy: EconomyManager) : CommandExecutor, TabCompleter {

    private val pageSize = 10

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val currency = when (args.getOrNull(0)?.lowercase()) {
            null, "money" -> Currency.MONEY
            "shards" -> Currency.SHARDS
            else -> {
                sender.sendMessage(Component.text("Usage: /baltop [money|shards] [page]").color(NamedTextColor.RED))
                return true
            }
        }

        val top = economy.top(currency)
        val pages = maxOf(1, (top.size + pageSize - 1) / pageSize)
        val page = (args.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, pages)

        val (title, color) = when (currency) {
            Currency.MONEY -> "Top Money" to NamedTextColor.GREEN
            Currency.SHARDS -> "Top Shards" to NamedTextColor.LIGHT_PURPLE
        }
        sender.sendMessage(
            Component.text()
                .append(Component.text("$title ", color, TextDecoration.BOLD))
                .append(Component.text("(page $page/$pages)", NamedTextColor.GRAY))
                .build()
        )

        top.drop((page - 1) * pageSize).take(pageSize).forEachIndexed { i, (uuid, account) ->
            val rank = (page - 1) * pageSize + i + 1
            sender.sendMessage(
                Component.text("#$rank ", NamedTextColor.GRAY)
                    .append(Component.text("${account.name} ", NamedTextColor.WHITE))
                    .append(Component.text(EconomyManager.format(economy.balance(uuid, currency)), color))
            )
        }

        if (sender is Player) {
            val rank = top.indexOfFirst { it.first == sender.uniqueId } + 1
            if (rank > 0) {
                sender.sendMessage(Component.text("Your rank: #$rank", NamedTextColor.YELLOW))
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        if (args.size == 1) listOf("money", "shards").filter { it.startsWith(args[0].lowercase()) } else emptyList()
}
