package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

// Admin command for changing balances: /eco <give|take|set> <player> <money|shards> <amount>
class EcoCommand(private val economy: EconomyManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val usage = Component.text("Usage: /eco <give|take|set> <player> <money|shards> <amount>").color(NamedTextColor.RED)
        if (args.size != 4) {
            sender.sendMessage(usage)
            return true
        }

        val action = args[0].lowercase()
        val target = economy.findByName(args[1])
        val currency = when (args[2].lowercase()) {
            "money" -> Currency.MONEY
            "shards" -> Currency.SHARDS
            else -> null
        }
        val amount = args[3].toDoubleOrNull()

        if (action !in listOf("give", "take", "set") || currency == null || amount == null || amount < 0) {
            sender.sendMessage(usage)
            return true
        }
        if (target == null) {
            sender.sendMessage(Component.text("${args[1]} has never joined this server.").color(NamedTextColor.RED))
            return true
        }

        val (uuid, account) = target
        val current = economy.balance(uuid, currency)
        val newBalance = when (action) {
            "give" -> current + amount
            "take" -> current - amount
            else -> amount
        }
        economy.set(uuid, currency, newBalance)

        val currencyName = currency.name.lowercase()
        sender.sendMessage(
            Component.text("${account.name} now has ${EconomyManager.format(economy.balance(uuid, currency))} $currencyName.")
                .color(NamedTextColor.YELLOW)
        )
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val options = when (args.size) {
            1 -> listOf("give", "take", "set")
            2 -> sender.server.onlinePlayers.map { it.name }
            3 -> listOf("money", "shards")
            else -> emptyList()
        }
        return options.filter { it.lowercase().startsWith(args.last().lowercase()) }
    }
}
