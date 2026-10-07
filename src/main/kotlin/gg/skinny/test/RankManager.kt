package gg.skinny.test

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Scoreboard
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// A permission a rank unlocks, and the command it unlocks for the /grants menu.
class Perk(val permission: String, val label: String)

// Highest rank first. tag is shown before the player's name; MEMBER has none.
// Each rank also gets the perks of every rank below it.
enum class Rank(
    val displayName: String,
    val tag: String?,
    val color: TextColor,
    val icon: Material,
    val perks: List<Perk>,
) {
    OWNER("Owner", "OWNER", NamedTextColor.DARK_RED, Material.NETHER_STAR, listOf(
        Perk("minecraft.command.op", "/op"),
        Perk("minecraft.command.deop", "/deop"),
        Perk("minecraft.command.whitelist", "/whitelist"),
        Perk("minecraft.command.stop", "/stop"),
    )),
    DEVELOPER("Developer", "DEV", NamedTextColor.AQUA, Material.COMMAND_BLOCK, listOf(
        Perk("skinny.grants", "/grants"),
    )),
    ADMIN("Admin", "ADMIN", NamedTextColor.RED, Material.REDSTONE_BLOCK, listOf(
        Perk("skinny.gamemode.creative", "/gmc"),
        Perk("minecraft.command.gamemode", "/gamemode"),
        Perk("minecraft.command.give", "/give"),
        Perk("skinny.build", "/build"),
        Perk("skinny.setspawn", "/setspawn"),
        Perk("skinny.eco", "/economy add, take, set"),
    )),
    SR_MOD("Sr. Mod", "SR.MOD", NamedTextColor.GREEN, Material.DIAMOND_SWORD, listOf(
        Perk("minecraft.command.ban-ip", "/ban-ip"),
        Perk("minecraft.command.pardon-ip", "/pardon-ip"),
        Perk("skinny.speed", "/speed"),
    )),
    MOD("Mod", "MOD", NamedTextColor.DARK_GREEN, Material.IRON_SWORD, listOf(
        Perk("minecraft.command.teleport", "/tp"),
        Perk("minecraft.command.ban", "/ban"),
        Perk("minecraft.command.pardon", "/pardon"),
        Perk("minecraft.command.banlist", "/banlist"),
        Perk("skinny.gamemode.spectator", "/gmsp"),
        Perk("skinny.gamemode.survival", "/gms"),
    )),
    HELPER("Helper", "HELPER", NamedTextColor.YELLOW, Material.BOOK, listOf(
        Perk("minecraft.command.kick", "/kick"),
    )),
    MEDIA("Media", "MEDIA", NamedTextColor.LIGHT_PURPLE, Material.SPYGLASS, listOf(
        Perk("skinny.heal", "/heal"),
    )),
    SKINNY_PLUS("Skinny+", "SKINNY+", TextColor.color(0x3FA9F5), Material.DIAMOND, listOf(
        Perk("skinny.feed", "/feed"),
    )),
    MEMBER("Member", null, NamedTextColor.GRAY, Material.PAPER, emptyList());

    // Teams sort alphabetically in the tab list, so the number keeps higher ranks on top.
    val teamName = "rank_%02d".format(ordinal)

    fun isAbove(other: Rank) = ordinal < other.ordinal

    // The rank directly below this one, whose perks this rank also has.
    fun below(): Rank? = values().getOrNull(ordinal + 1)

    fun allPermissions(): List<String> =
        values().filter { it.ordinal >= ordinal }.flatMap { it.perks }.map { it.permission }

    fun prefix(): Component =
        if (tag == null) Component.empty()
        else Component.text(tag, color, TextDecoration.BOLD).append(Component.text(" "))

    // Player name colored for this rank, with its tag in front.
    fun format(name: String): Component = prefix().append(Component.text(name, color))
}

// Stores each player's rank in plugins/TestPlugin/ranks.yml, shows it in chat, the tab list, and above heads,
// and gives online players their rank's permissions.
class RankManager(private val plugin: JavaPlugin) : Listener {

    private val file = File(plugin.dataFolder, "ranks.yml")

    // Chat is rendered off the main thread, so this map is read concurrently.
    private val ranks = ConcurrentHashMap<UUID, Rank>()

    private val attachments = mutableMapOf<UUID, PermissionAttachment>()

    fun load() {
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (key in yaml.getKeys(false)) {
            val rank = yaml.getString(key)?.let { name -> Rank.values().firstOrNull { it.name == name } } ?: continue
            ranks[UUID.fromString(key)] = rank
        }
    }

    private fun save() {
        val yaml = YamlConfiguration()
        for ((uuid, rank) in ranks) yaml.set(uuid.toString(), rank.name)
        plugin.dataFolder.mkdirs()
        yaml.save(file)
    }

    fun rank(uuid: UUID): Rank = ranks[uuid] ?: Rank.MEMBER

    fun setRank(uuid: UUID, rank: Rank) {
        if (rank == Rank.MEMBER) ranks.remove(uuid) else ranks[uuid] = rank
        save()
        refreshTeams()
        plugin.server.getPlayer(uuid)?.let(::applyPermissions)
    }

    fun applyAllPermissions() = plugin.server.onlinePlayers.forEach(::applyPermissions)

    private fun applyPermissions(player: Player) {
        attachments.remove(player.uniqueId)?.remove()
        val attachment = player.addAttachment(plugin)
        rank(player.uniqueId).allPermissions().forEach { attachment.setPermission(it, true) }
        attachments[player.uniqueId] = attachment
        // Resends the command list so newly unlocked commands tab complete.
        player.updateCommands()
    }

    // Every player has their own sidebar scoreboard, so every board needs the rank teams.
    fun refreshTeams() {
        val online = plugin.server.onlinePlayers
        for (viewer in online) {
            val board = viewer.scoreboard
            for (player in online) team(board, rank(player.uniqueId)).addEntry(player.name)
        }
    }

    private fun team(board: Scoreboard, rank: Rank) =
        board.getTeam(rank.teamName) ?: board.registerNewTeam(rank.teamName).apply {
            prefix(rank.prefix())
            color(NamedTextColor.nearestTo(rank.color))
        }

    // Runs after ScoreboardManager has given the player their sidebar board.
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        applyPermissions(event.player)
        refreshTeams()
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        attachments.remove(event.player.uniqueId)?.remove()
    }

    @EventHandler
    fun onChat(event: AsyncChatEvent) {
        val rank = rank(event.player.uniqueId)
        event.renderer { source, _, message, _ ->
            rank.format(source.name)
                .append(Component.text(": ", NamedTextColor.DARK_GRAY))
                .append(message.color(NamedTextColor.WHITE))
        }
    }
}
