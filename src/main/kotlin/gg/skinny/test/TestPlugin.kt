package gg.skinny.test

import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEggThrowEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.plugin.java.JavaPlugin
import java.awt.Component

class TestPlugin : JavaPlugin(), Listener {

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        event.isCancelled = true
    }

    @EventHandler
    fun onPlayerEggThrow(event: PlayerEggThrowEvent) {
        event.player.sendMessage(net.kyori.adventure.text.Component.text("u threw a mf egg or sum shit!").color(NamedTextColor.AQUA))
    }
}