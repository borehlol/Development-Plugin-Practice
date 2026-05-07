package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.gametest.framework.TestCommand
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEggThrowEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin


class TestPlugin : JavaPlugin(), Listener {

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        getCommand("heal")?.setExecutor(HealCommand())
        getCommand("gmc")?.setExecutor(GmcCommand())
        getCommand("gms")?.setExecutor(GmsCommand())
        getCommand("gmsp")?.setExecutor(GmspCommand())
        getCommand("feed")?.setExecutor(FeedCommand())
    }


    @EventHandler
    fun onPlayerEggThrow(event: PlayerEggThrowEvent) {
        event.player.sendMessage(Component.text("u threw a mf egg or sum shit!").color(NamedTextColor.AQUA))
    }




}
