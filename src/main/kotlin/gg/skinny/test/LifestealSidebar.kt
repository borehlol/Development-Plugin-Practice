package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

// SkinnySMP sidebar, shown only on the lifesteal server.
class LifestealSidebar(plugin: JavaPlugin, private val economy: EconomyManager) : Sidebar(plugin) {

    override val title: Component = Component.text("Skinny", titleBlue, TextDecoration.BOLD)
        .append(Component.text("SMP", NamedTextColor.WHITE, TextDecoration.BOLD))

    override fun lines(player: Player): List<Component> {
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

    private fun stat(icon: String, iconColor: NamedTextColor, label: String, value: String): Component =
        Component.text(icon, iconColor)
            .append(Component.text(label, NamedTextColor.WHITE))
            .append(Component.text(value, iconColor))

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
