package gg.skinny.test

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerEggThrowEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File


class TestPlugin : JavaPlugin(), Listener {

    private lateinit var economy: EconomyManager
    private lateinit var ranks: RankManager

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        getCommand("heal")?.setExecutor(HealCommand())
        getCommand("gmc")?.setExecutor(GmcCommand())
        getCommand("gms")?.setExecutor(GmsCommand())
        getCommand("gmsp")?.setExecutor(GmspCommand())
        getCommand("feed")?.setExecutor(FeedCommand())
        val spawnCommand = SpawnCommand(this)
        server.pluginManager.registerEvents(spawnCommand, this)
        getCommand("spawn")?.setExecutor(spawnCommand)
        getCommand("setspawn")?.setExecutor(SetSpawnCommand(this))
        getCommand("middle")?.setExecutor(MiddleCommand())
        getCommand("speed")?.setExecutor(SpeedCommand())

        val buildCommand = BuildCommand()
        server.pluginManager.registerEvents(buildCommand, this)
        getCommand("build")?.setExecutor(buildCommand)

        val rtpCommand = RtpCommand(this)
        server.pluginManager.registerEvents(rtpCommand, this)
        getCommand("rtp")?.setExecutor(rtpCommand)

        economy = EconomyManager(this)
        economy.load()
        server.pluginManager.registerEvents(economy, this)
        getCommand("baltop")?.setExecutor(BaltopCommand(economy))
        getCommand("economy")?.setExecutor(EconomyCommand(economy))
        getCommand("pay")?.setExecutor(PayCommand(economy))

        ranks = RankManager(this, createRankStore())
        ranks.load()
        server.pluginManager.registerEvents(ranks, this)
        ranks.startSync()

        val sidebar = when (NetworkServer.type) {
            ServerType.LIFESTEAL -> LifestealSidebar(this, economy)
            ServerType.LOBBY -> {
                val playerCount = NetworkPlayerCount(this)
                playerCount.start()
                LobbySidebar(this, ranks, playerCount)
            }
            else -> null
        }
        sidebar?.let {
            server.pluginManager.registerEvents(it, this)
            it.start()
        }

        // After the sidebars exist, since each player's sidebar board needs the rank teams.
        ranks.refreshTeams()
        ranks.applyAllPermissions()

        val grantsCommand = GrantsCommand(this, ranks, economy)
        server.pluginManager.registerEvents(grantsCommand, this)
        getCommand("grants")?.setExecutor(grantsCommand)
    }

    override fun onDisable() {
        if (::economy.isInitialized) economy.save()
        if (::ranks.isInitialized) ranks.close()
    }

    // MySQL when running on the network (set in network/docker-compose.yml), otherwise ranks.yml.
    private fun createRankStore(): RankStore {
        val file = File(dataFolder, "ranks.yml")
        val host = System.getenv("SKINNY_DB_HOST") ?: return YamlRankStore(file)
        val store = MySqlRankStore(
            "jdbc:mysql://$host:3306/${System.getenv("SKINNY_DB_NAME")}",
            System.getenv("SKINNY_DB_USER"),
            System.getenv("SKINNY_DB_PASSWORD"),
        )

        // One-time import of the ranks this server saved before they were shared across the network.
        if (file.exists()) {
            val imported = YamlRankStore(file).loadAll()
            for ((uuid, entry) in imported) {
                val name = entry.name.ifEmpty { server.getOfflinePlayer(uuid).name ?: "unknown" }
                store.setRank(uuid, name, entry.rank)
            }
            file.renameTo(File(dataFolder, "ranks.yml.imported"))
            logger.info("Imported ${imported.size} ranks from ranks.yml into MySQL")
        }
        return store
    }


    @EventHandler
    fun onPlayerEggThrow(event: PlayerEggThrowEvent) {
        event.player.sendMessage(Component.text("u threw a mf egg or sum shit!").color(NamedTextColor.AQUA))
    }




}
