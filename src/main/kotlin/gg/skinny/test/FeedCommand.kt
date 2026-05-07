package gg.skinny.test

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class FeedCommand : CommandExecutor {

//   feeds the player
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.size != 2) {
            sender.sendPlainMessage("Invalid usage, make to use /feed [player] [amount]")
            return false
        }

//checks if  its a player or na
        val player = Bukkit.getPlayer(args[0])
        if (player == null) {
            sender.sendPlainMessage("Player not found")
            return false
        }
//argument lets see
        val amount = args[1].toIntOrNull()
        if (amount == null) {
            sender.sendPlainMessage("Specify a valid number")
            return false
        }

        player.foodLevel = (amount + player.foodLevel).coerceIn(0, 20)
//send and recieve a msg depending on if u fed someone or if you got fed
        sender.sendPlainMessage("You have fed ${player.name}.")
        player.sendPlainMessage("${sender.name} has fed you.")
    return false
    }


}