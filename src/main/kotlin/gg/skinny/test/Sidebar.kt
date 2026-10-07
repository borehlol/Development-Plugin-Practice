package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
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

// A per-player sidebar. Each line is a team whose prefix holds the text,
// so lines can be updated every second without the sidebar flickering.
// Subclasses pick the title and lines; the number of lines must stay the same.
abstract class Sidebar(protected val plugin: JavaPlugin) : Listener {

    private val boards = mutableMapOf<UUID, Scoreboard>()

    protected abstract val title: Component

    protected abstract fun lines(player: Player): List<Component>

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

    // Every line needs a unique, invisible entry name; color codes render as nothing.
    private fun entryFor(index: Int): String =
        "§" + "0123456789abcdef"[index] + "§r"

    protected fun gradient(text: String, from: TextColor, to: TextColor): Component {
        val builder = Component.text()
        text.forEachIndexed { i, c ->
            val t = if (text.length > 1) i.toFloat() / (text.length - 1) else 0f
            builder.append(Component.text(c, TextColor.lerp(t, from, to)))
        }
        return builder.build()
    }

    protected fun label(label: String, value: String, valueColor: TextColor): Component =
        Component.text(label, NamedTextColor.WHITE).append(Component.text(value, valueColor))

    companion object {
        val titleBlue = TextColor.color(0x3FA9F5)
        val gradientStart = TextColor.color(0x8FD3FF)
    }
}
