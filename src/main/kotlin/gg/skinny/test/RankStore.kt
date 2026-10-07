package gg.skinny.test

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

class RankEntry(val name: String, val rank: Rank)

// Where ranks are saved. RankManager only calls these off the main thread, since they can block.
interface RankStore {
    // Every player the store knows about, including members, so /grants can find them by name.
    fun loadAll(): Map<UUID, RankEntry>

    // Saves the player's current name and returns their saved rank.
    fun recordJoin(uuid: UUID, name: String): RankEntry

    fun setRank(uuid: UUID, name: String, rank: Rank)
}

// Shared by every server on the network, so a rank granted on one shows up on all of them.
// Connection details come from network/docker-compose.yml.
class MySqlRankStore(private val url: String, private val user: String, private val password: String) : RankStore {

    init {
        connect().use {
            // "rank" is a reserved word in MySQL, hence rank_name.
            it.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS player_ranks (
                    uuid CHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    rank_name VARCHAR(32) NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    // A new connection per call; ranks change rarely, so pooling isn't worth it.
    private fun connect(): Connection = DriverManager.getConnection(url, user, password)

    override fun loadAll(): Map<UUID, RankEntry> = connect().use { connection ->
        val result = mutableMapOf<UUID, RankEntry>()
        connection.createStatement().executeQuery("SELECT uuid, name, rank_name FROM player_ranks").use { rows ->
            while (rows.next()) {
                result[UUID.fromString(rows.getString(1))] = RankEntry(rows.getString(2), parseRank(rows.getString(3)))
            }
        }
        result
    }

    override fun recordJoin(uuid: UUID, name: String): RankEntry = connect().use { connection ->
        // Only the name is updated, so a rank granted elsewhere is never overwritten.
        connection.prepareStatement(
            "INSERT INTO player_ranks (uuid, name, rank_name) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = VALUES(name)"
        ).use {
            it.setString(1, uuid.toString())
            it.setString(2, name)
            it.setString(3, Rank.MEMBER.name)
            it.executeUpdate()
        }
        connection.prepareStatement("SELECT rank_name FROM player_ranks WHERE uuid = ?").use {
            it.setString(1, uuid.toString())
            it.executeQuery().use { rows ->
                RankEntry(name, if (rows.next()) parseRank(rows.getString(1)) else Rank.MEMBER)
            }
        }
    }

    override fun setRank(uuid: UUID, name: String, rank: Rank) = connect().use { connection ->
        connection.prepareStatement(
            "INSERT INTO player_ranks (uuid, name, rank_name) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE rank_name = VALUES(rank_name)"
        ).use {
            it.setString(1, uuid.toString())
            it.setString(2, name)
            it.setString(3, rank.name)
            it.executeUpdate()
        }
        Unit
    }

    private fun parseRank(name: String) = Rank.values().firstOrNull { it.name == name } ?: Rank.MEMBER
}

// For a server running on its own (./gradlew runServer) with no database: plugins/TestPlugin/ranks.yml.
class YamlRankStore(private val file: File) : RankStore {

    private val entries = mutableMapOf<UUID, RankEntry>()

    init {
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (key in yaml.getKeys(false)) {
            val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            // Older files stored only the rank: "<uuid>: OWNER".
            val section = yaml.getConfigurationSection(key)
            val rankName = section?.getString("rank") ?: yaml.getString(key)
            val rank = Rank.values().firstOrNull { it.name == rankName } ?: continue
            entries[uuid] = RankEntry(section?.getString("name") ?: "", rank)
        }
    }

    @Synchronized
    override fun loadAll(): Map<UUID, RankEntry> = entries.toMap()

    @Synchronized
    override fun recordJoin(uuid: UUID, name: String): RankEntry {
        val entry = RankEntry(name, entries[uuid]?.rank ?: Rank.MEMBER)
        if (entries[uuid]?.name != name) {
            entries[uuid] = entry
            save()
        }
        return entry
    }

    @Synchronized
    override fun setRank(uuid: UUID, name: String, rank: Rank) {
        entries[uuid] = RankEntry(name, rank)
        save()
    }

    private fun save() {
        val yaml = YamlConfiguration()
        for ((uuid, entry) in entries) {
            yaml.set("$uuid.name", entry.name)
            yaml.set("$uuid.rank", entry.rank.name)
        }
        file.parentFile.mkdirs()
        yaml.save(file)
    }
}
