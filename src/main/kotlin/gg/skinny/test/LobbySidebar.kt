package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// SkinnyMC sidebar, shown only in the lobby.
class LobbySidebar(
    plugin: JavaPlugin,
    private val ranks: RankManager,
    private val playerCount: NetworkPlayerCount,
) : Sidebar(plugin) {

    private val dateFormat = DateTimeFormatter.ofPattern("MM/dd/yy")

    override val title: Component = Component.text("Skinny", titleBlue, TextDecoration.BOLD)
        .append(Component.text("MC", NamedTextColor.WHITE, TextDecoration.BOLD))

    override fun lines(player: Player): List<Component> {
        val rank = ranks.rank(player.uniqueId)

        return listOf(
            Component.text(LocalDate.now().format(dateFormat), NamedTextColor.GRAY),
            Component.empty(),
            label("Rank: ", rank.displayName, rank.color),
            label("Network Level: ", networkLevel(player).toString(), titleBlue),
            Component.empty(),
            label("Lobby: ", "#${NetworkServer.number}", titleBlue),
            label("Players: ", playerCount.count.toString(), titleBlue),
            Component.empty(),
            Component.text(NetworkServer.ip, titleBlue),
        )
    }

    // TODO: network levels aren't tracked yet.
    private fun networkLevel(player: Player): Int = 0
}
