package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class PayCommand(private val economy: EconomyManager) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command.").color(NamedTextColor.RED))
            return true
        }

        val amount = args.getOrNull(1)?.let(EconomyManager::parseAmount)
        if (args.size != 2 || amount == null || amount == 0.0) {
            sender.sendMessage(Component.text("Usage: /pay <player> <amount>").color(NamedTextColor.RED))
            return true
        }

        val target = economy.findByName(args[0])
        if (target == null) {
            sender.sendMessage(Component.text("${args[0]} has never joined this server.").color(NamedTextColor.RED))
            return true
        }

        val (targetId, targetAccount) = target
        if (targetId == sender.uniqueId) {
            sender.sendMessage(Component.text("You can't pay yourself.").color(NamedTextColor.RED))
            return true
        }

        val balance = economy.balance(sender.uniqueId, Currency.MONEY)
        if (balance < amount) {
            sender.sendMessage(
                Component.text("You only have $${EconomyManager.format(balance)}.").color(NamedTextColor.RED)
            )
            return true
        }

        economy.set(sender.uniqueId, Currency.MONEY, balance - amount)
        economy.set(targetId, Currency.MONEY, economy.balance(targetId, Currency.MONEY) + amount)

        val formatted = EconomyManager.format(amount)
        sender.sendMessage(
            Component.text("You paid ", NamedTextColor.YELLOW)
                .append(Component.text(targetAccount.name, NamedTextColor.WHITE))
                .append(Component.text(" $$formatted", NamedTextColor.GREEN))
        )
        sender.server.getPlayer(targetId)?.sendMessage(
            Component.text(sender.name, NamedTextColor.WHITE)
                .append(Component.text(" paid you ", NamedTextColor.YELLOW))
                .append(Component.text("$$formatted", NamedTextColor.GREEN))
        )
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> =
        if (args.size == 1) {
            sender.server.onlinePlayers.map { it.name }
                .filter { it != sender.name && it.lowercase().startsWith(args[0].lowercase()) }
        } else emptyList()
}
