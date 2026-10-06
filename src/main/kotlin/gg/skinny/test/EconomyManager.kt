package gg.skinny.test

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID

class Account(var name: String, var money: Double = 0.0, var shards: Long = 0)

enum class Currency { MONEY, SHARDS }

// Stores money and shards for every player who has ever joined, in plugins/TestPlugin/players.yml.
class EconomyManager(private val plugin: JavaPlugin) : Listener {

    private val file = File(plugin.dataFolder, "players.yml")
    private val accounts = mutableMapOf<UUID, Account>()

    fun load() {
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (key in yaml.getKeys(false)) {
            val section = yaml.getConfigurationSection(key) ?: continue
            accounts[UUID.fromString(key)] = Account(
                section.getString("name") ?: "Unknown",
                section.getDouble("money"),
                section.getLong("shards"),
            )
        }

        // Players who joined before the economy existed still get an account.
        for (player in plugin.server.offlinePlayers) {
            val name = player.name ?: continue
            accounts.getOrPut(player.uniqueId) { Account(name) }
        }
        save()
    }

    fun save() {
        val yaml = YamlConfiguration()
        for ((uuid, account) in accounts) {
            yaml.set("$uuid.name", account.name)
            yaml.set("$uuid.money", account.money)
            yaml.set("$uuid.shards", account.shards)
        }
        plugin.dataFolder.mkdirs()
        yaml.save(file)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        accounts.getOrPut(player.uniqueId) { Account(player.name) }.name = player.name
        save()
    }

    fun account(uuid: UUID): Account? = accounts[uuid]

    fun findByName(name: String): Pair<UUID, Account>? =
        accounts.entries.firstOrNull { it.value.name.equals(name, ignoreCase = true) }?.toPair()

    fun balance(uuid: UUID, currency: Currency): Double {
        val account = accounts[uuid] ?: return 0.0
        return when (currency) {
            Currency.MONEY -> account.money
            Currency.SHARDS -> account.shards.toDouble()
        }
    }

    fun set(uuid: UUID, currency: Currency, amount: Double) {
        val account = accounts[uuid] ?: return
        when (currency) {
            Currency.MONEY -> account.money = amount.coerceAtLeast(0.0)
            Currency.SHARDS -> account.shards = amount.toLong().coerceAtLeast(0)
        }
        save()
    }

    // Every account, richest first.
    fun top(currency: Currency): List<Pair<UUID, Account>> =
        accounts.entries
            .map { it.toPair() }
            .sortedByDescending { balance(it.first, currency) }

    companion object {
        fun format(value: Double): String = when {
            value >= 1_000_000_000 -> "%.1fB".format(value / 1_000_000_000)
            value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
            value >= 1_000 -> "%.1fK".format(value / 1_000)
            else -> "%.0f".format(value)
        }
    }
}
