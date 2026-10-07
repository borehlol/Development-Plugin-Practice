package gg.skinny.test

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.permissions.PermissionAttachment
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Scoreboard
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

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

// Keeps each player's rank in a RankStore (MySQL on the network), shows it in chat, the tab list,
// and above heads, and gives online players their rank's permissions.
class RankManager(private val plugin: JavaPlugin, private val store: RankStore) : Listener {

    // A copy of the store, so ranks can be read without waiting on the database.
    // Chat is rendered off the main thread, so this map is read concurrently.
    private val entries = ConcurrentHashMap<UUID, RankEntry>()

    // Every store call runs here, one at a time and in order, so the main thread never waits on the database.
    private val database = Executors.newSingleThreadExecutor()

    // Players whose rank this server changed but hasn't finished saving. Only used on the main thread.
    private val pendingWrites = mutableMapOf<UUID, Int>()

    private val attachments = mutableMapOf<UUID, PermissionAttachment>()

    // Blocks, so only call it while the plugin is enabling.
    fun load() {
        entries.putAll(store.loadAll())
    }

    // Every 10 seconds, picks up ranks granted on other servers.
    fun startSync() {
        plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            database.execute {
                val loaded = try {
                    store.loadAll()
                } catch (e: Exception) {
                    plugin.logger.log(Level.WARNING, "Couldn't load ranks", e)
                    return@execute
                }
                runOnMainThread { applyLoaded(loaded) }
            }
        }, 200L, 200L)
    }

    // Waits for ranks still being saved, so a grant just before a restart isn't lost.
    fun close() {
        database.shutdown()
        if (!database.awaitTermination(10, TimeUnit.SECONDS)) {
            plugin.logger.severe("Gave up waiting for ranks to save")
        }
    }

    private fun applyLoaded(loaded: Map<UUID, RankEntry>) {
        var changed = false
        for ((uuid, entry) in loaded) {
            // This server's own newer change hasn't reached the database yet.
            if (uuid in pendingWrites) continue
            val old = entries.put(uuid, entry)?.rank ?: Rank.MEMBER
            if (old == entry.rank) continue

            val player = plugin.server.getPlayer(uuid) ?: continue
            changed = true
            applyPermissions(player)
            player.sendMessage(
                Component.text("Your rank is now ", NamedTextColor.GREEN)
                    .append(Component.text(entry.rank.displayName, entry.rank.color, TextDecoration.BOLD))
            )
        }
        if (changed) refreshTeams()
    }

    fun rank(uuid: UUID): Rank = entries[uuid]?.rank ?: Rank.MEMBER

    // The UUID and correctly capitalized name of a player who has joined any server on the network.
    fun findByName(name: String): Pair<UUID, String>? =
        entries.entries.firstOrNull { it.value.name.equals(name, ignoreCase = true) }?.let { it.key to it.value.name }

    // Takes effect here immediately; the returned future completes once it's saved.
    fun setRank(uuid: UUID, name: String, rank: Rank): CompletableFuture<Void> {
        entries[uuid] = RankEntry(name, rank)
        pendingWrites.merge(uuid, 1, Int::plus)
        refreshTeams()
        plugin.server.getPlayer(uuid)?.let(::applyPermissions)

        return CompletableFuture.runAsync({ store.setRank(uuid, name, rank) }, database)
            .whenComplete { _, error ->
                if (error != null) plugin.logger.log(Level.SEVERE, "Couldn't save $name's rank", error)
                runOnMainThread { pendingWrites.computeIfPresent(uuid) { _, count -> (count - 1).takeIf { it > 0 } } }
            }
    }

    // Scheduling fails once the plugin is disabling, and nothing needs updating by then.
    private fun runOnMainThread(task: () -> Unit) {
        if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable(task))
    }

    // Runs before the player is let in, off the main thread, so their rank is ready when they arrive.
    @EventHandler
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
        try {
            entries[event.uniqueId] = store.recordJoin(event.uniqueId, event.name)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Couldn't load ${event.name}'s rank; using the last one seen", e)
        }
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

    // Runs after the Sidebar has given the player their sidebar board.
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
