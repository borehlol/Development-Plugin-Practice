package gg.skinny.test

import com.google.common.io.ByteStreams
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener

// Asks the proxy how many players are online across the whole network, every two seconds.
// Velocity answers on the BungeeCord plugin message channel, but only through a connected
// player, so the count only updates while someone is on this server.
class NetworkPlayerCount(private val plugin: JavaPlugin) : PluginMessageListener {

    private var lastCount = 0

    // Never lower than this server's own count, e.g. before the proxy's first answer.
    val count: Int get() = maxOf(lastCount, plugin.server.onlinePlayers.size)

    fun start() {
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, CHANNEL)
        plugin.server.messenger.registerIncomingPluginChannel(plugin, CHANNEL, this)
        plugin.server.scheduler.runTaskTimer(plugin, Runnable { request() }, 20L, 40L)
    }

    private fun request() {
        val player = plugin.server.onlinePlayers.firstOrNull() ?: return
        val out = ByteStreams.newDataOutput()
        out.writeUTF("PlayerCount")
        out.writeUTF("ALL")
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray())
    }

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != CHANNEL) return
        val input = ByteStreams.newDataInput(message)
        if (input.readUTF() != "PlayerCount") return
        input.readUTF() // the server asked about, "ALL"
        lastCount = input.readInt()
    }

    companion object {
        private const val CHANNEL = "BungeeCord"
    }
}
