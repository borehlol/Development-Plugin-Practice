package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID

// SkinnySMP sidebar. Each line is a team whose prefix holds the text,
// so lines can be updated every second without the sidebar flickering.
class ScoreboardManager(private val plugin: JavaPlugin, private val economy: EconomyManager) : Listener {

    private val boards = mutableMapOf<UUID, Scoreboard>()

    private val titleBlue = TextColor.color(0x3FA9F5)
    private val gradientStart = TextColor.color(0x8FD3FF)

    fun start() {
        plugin.server.onlinePlayers.forEach(::create)
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            plugin.server.onlinePlayers.forEach(::update)
        }, 20L, 20L)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) = create(event.player)

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        boards.remove(event.player.uniqueId)
    }

    private fun create(player: Player) {
        val board = plugin.server.scoreboardManager.newScoreboard
        val title = Component.text("Skinny", titleBlue, TextDecoration.BOLD)
            .append(Component.text("SMP", NamedTextColor.WHITE, TextDecoration.BOLD))
        val objective = board.registerNewObjective("sidebar", Criteria.DUMMY, title)
        objective.displaySlot = DisplaySlot.SIDEBAR

        // Scores count down so line 0 is shown at the top.
        val lineCount = lines(player).size
        for (i in 0 until lineCount) {
            val entry = entryFor(i)
            board.registerNewTeam("line$i").addEntry(entry)
            objective.getScore(entry).score = lineCount - i
        }

        boards[player.uniqueId] = board
        player.scoreboard = board
        update(player)
    }

    private fun update(player: Player) {
        val board = boards[player.uniqueId] ?: return
        lines(player).forEachIndexed { i, line ->
            board.getTeam("line$i")?.prefix(line)
        }
    }

    private fun lines(player: Player): List<Component> {
        val kills = player.getStatistic(Statistic.PLAYER_KILLS)
        val deaths = player.getStatistic(Statistic.DEATHS)
        val playtimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE)

        return listOf(
            Component.empty(),
            stat("$ ", NamedTextColor.GREEN, "Money ", EconomyManager.format(economy.balance(player.uniqueId, Currency.MONEY))),
            stat("★ ", NamedTextColor.LIGHT_PURPLE, "Shards ", EconomyManager.format(economy.balance(player.uniqueId, Currency.SHARDS))),
            stat("⚔ ", NamedTextColor.RED, "Kills ", kills.toString()),
            stat("☠ ", NamedTextColor.GOLD, "Deaths ", deaths.toString()),
            stat("⌚ ", NamedTextColor.YELLOW, "Playtime ", formatPlaytime(playtimeTicks)),
            Component.empty(),
            Component.text("NA West ", NamedTextColor.GRAY)
                .append(Component.text("[", NamedTextColor.GRAY))
                .append(gradient("${player.ping}ms", gradientStart, titleBlue))
                .append(Component.text("]", NamedTextColor.GRAY)),
        )
    }

    private fun gradient(text: String, from: TextColor, to: TextColor): Component {
        val builder = Component.text()
        text.forEachIndexed { i, c ->
            val t = if (text.length > 1) i.toFloat() / (text.length - 1) else 0f
            builder.append(Component.text(c, TextColor.lerp(t, from, to)))
        }
        return builder.build()
    }

    private fun stat(icon: String, iconColor: NamedTextColor, label: String, value: String): Component =
        Component.text(icon, iconColor)
            .append(Component.text(label, NamedTextColor.WHITE))
            .append(Component.text(value, iconColor))

    // Every line needs a unique, invisible entry name; color codes render as nothing.
    private fun entryFor(index: Int): String =
        "§" + "0123456789abcdef"[index] + "§r"

    private fun formatPlaytime(ticks: Int): String {
        val totalMinutes = ticks / 20 / 60
        val days = totalMinutes / 1440
        val hours = totalMinutes % 1440 / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}
